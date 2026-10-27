package com.iitrpr.calendar.admin;

import com.iitrpr.calendar.errors.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Campus calendar for admins: list, add, edit, delete and bulk import.
 * Students read the same table through StudentCampusController.
 */
@RestController
@RequestMapping("/api/admin/campus")
public class CampusController {

    public static class ItemPayload {
        public Long id;
        public String title;
        public String category;
        public String date;
        public String endDate;
        public String appliesTo;
        public String venue;
        public String description;
        public String followsDay;
    }

    public static class ImportPayload {
        public List<ItemPayload> rows;
    }

    public static final Set<String> CATEGORIES =
            Set.of("ACADEMIC", "EXAM", "HOLIDAY", "NOCLASS", "SWAP", "FEST", "TALK");

    @PersistenceContext
    private EntityManager em;

    private static final String SELECT_ALL = """
            SELECT id, title, category, start_date, end_date, applies_to, venue, description, follows_day
            FROM campus_item ORDER BY start_date, id
            """;

    // Shared with the student endpoint.
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listAll(EntityManager em) {
        List<Object[]> rows = em.createNativeQuery(SELECT_ALL).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("title", r[1]);
            m.put("category", r[2]);
            m.put("date", r[3].toString());
            m.put("endDate", r[4] == null ? "" : r[4].toString());
            m.put("appliesTo", r[5] == null ? "" : r[5]);
            m.put("venue", r[6] == null ? "" : r[6]);
            m.put("description", r[7] == null ? "" : r[7]);
            m.put("followsDay", r[8] == null ? "" : r[8]);
            out.add(m);
        }
        return out;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return listAll(em);
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody ItemPayload p) {
        check(p);
        try {
            Number id = (Number) em.createNativeQuery("""
                    INSERT INTO campus_item (title, category, start_date, end_date, applies_to, venue, description, follows_day)
                    VALUES (:t, :c, CAST(:d AS date), CAST(:e AS date), :a, :v, :ds, :f)
                    RETURNING id
                    """)
                    .setParameter("t", p.title.trim())
                    .setParameter("c", p.category.trim().toUpperCase())
                    .setParameter("d", p.date)
                    .setParameter("e", blank(p.endDate))
                    .setParameter("a", applies(p.appliesTo))
                    .setParameter("v", blank(p.venue))
                    .setParameter("ds", blank(p.description))
                    .setParameter("f", blank(p.followsDay))
                    .getSingleResult();
            return Map.of("ok", true, "id", id.longValue());
        } catch (jakarta.persistence.PersistenceException e) {
            throw new BadRequestException("An item with this title and start date already exists.");
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody ItemPayload p) {
        check(p);
        int n = em.createNativeQuery("""
                UPDATE campus_item SET title = :t, category = :c, start_date = CAST(:d AS date),
                       end_date = CAST(:e AS date), applies_to = :a, venue = :v, description = :ds,
                       follows_day = :f, updated_at = now()
                WHERE id = :id
                """)
                .setParameter("t", p.title.trim())
                .setParameter("c", p.category.trim().toUpperCase())
                .setParameter("d", p.date)
                .setParameter("e", blank(p.endDate))
                .setParameter("a", applies(p.appliesTo))
                .setParameter("v", blank(p.venue))
                .setParameter("ds", blank(p.description))
                .setParameter("f", blank(p.followsDay))
                .setParameter("id", id)
                .executeUpdate();
        if (n == 0) throw new BadRequestException("Item not found.");
        return Map.of("ok", true);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        em.createNativeQuery("DELETE FROM campus_item WHERE id = :id").setParameter("id", id).executeUpdate();
        return Map.of("ok", true);
    }

    // Rows are read and checked in the admin's browser. Every row is checked again here.
    // Items with the same title and start date are updated, not duplicated.
    @PostMapping("/import")
    @Transactional
    public Map<String, Object> importRows(@RequestBody ImportPayload body) {
        if (body == null || body.rows == null || body.rows.isEmpty()) {
            throw new BadRequestException("Nothing to import.");
        }
        int saved = 0;
        for (ItemPayload p : body.rows) {
            check(p);
            em.createNativeQuery("""
                    INSERT INTO campus_item (title, category, start_date, end_date, applies_to, venue, description, follows_day)
                    VALUES (:t, :c, CAST(:d AS date), CAST(:e AS date), :a, :v, :ds, :f)
                    ON CONFLICT (title, start_date) DO UPDATE SET
                        category = EXCLUDED.category, end_date = EXCLUDED.end_date, applies_to = EXCLUDED.applies_to,
                        venue = EXCLUDED.venue, description = EXCLUDED.description,
                        follows_day = EXCLUDED.follows_day, updated_at = now()
                    """)
                    .setParameter("t", p.title.trim())
                    .setParameter("c", p.category.trim().toUpperCase())
                    .setParameter("d", p.date)
                    .setParameter("e", blank(p.endDate))
                    .setParameter("a", applies(p.appliesTo))
                    .setParameter("v", blank(p.venue))
                    .setParameter("ds", blank(p.description))
                    .setParameter("f", blank(p.followsDay))
                    .executeUpdate();
            saved++;
        }
        return Map.of("saved", saved);
    }

    private void check(ItemPayload p) {
        if (p == null || p.title == null || p.title.isBlank()) throw new BadRequestException("Title is required.");
        if (p.date == null || p.date.isBlank()) throw new BadRequestException("Start date is required.");
        if (p.category == null || !CATEGORIES.contains(p.category.trim().toUpperCase())) {
            throw new BadRequestException("Unknown category: " + p.category);
        }
        if (p.endDate != null && !p.endDate.isBlank() && p.endDate.compareTo(p.date) < 0) {
            throw new BadRequestException("The end date is before the start date.");
        }
    }

    private String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String applies(String s) {
        return (s == null || s.isBlank()) ? "All students" : s.trim();
    }
}
