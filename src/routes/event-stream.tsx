import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useEffect } from "react";

export const Route = createFileRoute("/event-stream")({
  component: RouteComponent,
});

interface EventData {
  id: number;
  message: string;
  timestamp: string;
}

const useEventSource = (url: string) => {
  const queryClient = useQueryClient();

  useEffect(() => {
    const eventSource = new EventSource(url);

    eventSource.addEventListener("UPDATE", (event) => {
      const newData = JSON.parse(event.data) as EventData;
      queryClient.setQueryData<EventData[]>(["events"], (oldData = []) =>
        [newData, ...oldData].slice(0, 5),
      );
      console.log("Event received:", newData);
    });

    eventSource.onerror = (error) => {
      console.error("EventSource error:", error);

      // Giving the browser a few seconds to attempt reconnection
      setTimeout(() => {
        eventSource.close();
      }, 3000);
    };

    return () => {
      eventSource.close();
    };
  }, [url]);
};

const apiStreamUrl = `${import.meta.env.VITE_API_URL}/api/events`;
const apiUrl = `${import.meta.env.VITE_API_URL}/api/latest`;

function RouteComponent() {
  const {
    data: events,
    isLoading,
    isError,
    error,
  } = useQuery<EventData[]>({
    queryKey: ["events"],
    queryFn: async () => {
      const response = await fetch(apiUrl);
      return response.json();
    },
    staleTime: 5000,
    refetchInterval: 5000,
  });

  console.log("apiStreamUrl", apiStreamUrl);
  console.log("apiUrl", apiUrl);

  useEventSource(apiStreamUrl);

  if (isLoading) {
    return <div>Loading latest data...</div>;
  }

  if (isError) {
    console.error("Error loading latest data:", error);
    return <div>Error loading latest data</div>;
  }

  return (
    <div className="p-4">
      <h1 className="text-2xl mb-4">Event Stream</h1>
      <div className="flex flex-col gap-4">
        {events?.map((event) => (
          <div key={event.id}>
            <p>{event.id}</p>
            <p>{event.message}</p>
            <p>{new Date(event.timestamp).toLocaleTimeString()}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
