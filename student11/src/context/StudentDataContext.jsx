import { createContext, useContext } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { studentApi } from "../api/studentApi";
import { useAuth } from "./AuthContext";

const StudentDataContext = createContext(null);

export function StudentDataProvider({ children }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const enabled = !!user; // do not call the backend before login
  const key = (name) => [name, user?.email]; // keeps users' data separate

  const coursesQ = useQuery({ queryKey: key("courses"), queryFn: studentApi.getCourses, enabled });
  const electivesQ = useQuery({ queryKey: key("electives"), queryFn: studentApi.getElectives, enabled });
  const scheduleQ = useQuery({ queryKey: key("schedule"), queryFn: studentApi.getSchedule, enabled });

  const save = useMutation({
    mutationFn: studentApi.saveElectives,
    onMutate: async (ids) => {
      // update the checkbox instantly, undo if the server fails
      await qc.cancelQueries({ queryKey: key("electives") });
      const previous = qc.getQueryData(key("electives"));
      qc.setQueryData(key("electives"), ids);
      return { previous };
    },
    onError: (_err, _ids, ctx) => qc.setQueryData(key("electives"), ctx.previous),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: key("electives") });
      qc.invalidateQueries({ queryKey: key("schedule") });
    },
  });

  const electiveIds = electivesQ.data ?? [];

  const toggleElective = (id) => {
    const next = electiveIds.includes(id)
      ? electiveIds.filter((x) => x !== id)
      : [...electiveIds, id];
    save.mutate(next);
  };

  return (
    <StudentDataContext.Provider
      value={{
        courses: coursesQ.data ?? [],
        electiveIds,
        toggleElective,
        mySlots: scheduleQ.data?.slots ?? [],
        events: scheduleQ.data?.events ?? [],
        isLoading: coursesQ.isLoading || electivesQ.isLoading || scheduleQ.isLoading,
        isError: coursesQ.isError || electivesQ.isError || scheduleQ.isError,
      }}
    >
      {children}
    </StudentDataContext.Provider>
  );
}

export const useStudentData = () => useContext(StudentDataContext);