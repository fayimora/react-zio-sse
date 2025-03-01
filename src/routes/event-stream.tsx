import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";

export const Route = createFileRoute("/event-stream")({
  component: RouteComponent,
});

interface EventData {
  id: number;
  message: string;
  timestamp: string;
}

const useEventSource = (url: string) => {
  const [data, setData] = useState<EventData[]>([]);

  useEffect(() => {
    const eventSource = new EventSource(url);

    eventSource.addEventListener("UPDATE", (event) => {
      const newData = JSON.parse(event.data) as EventData;
      console.log("Event received:", newData);
      setData((prev) => [newData, ...prev].slice(0, 5)); // Keep last 10 events
    });

    eventSource.onerror = (error) => {
      console.error("EventSource error:", error);
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [url]);

  return { data };
};

const apiStreamUrl = "http://localhost:8090/api/events";
const apiUrl = "http://localhost:8090/api/initial";

function RouteComponent() {
  const { isLoading: isInitialDataLoading } = useQuery<EventData[]>({
    queryKey: ["events"],
    queryFn: async () => {
      const response = await fetch(apiUrl);
      return response.json();
    },
    // Only fetch this once at the beginning
    staleTime: Infinity,
  });

  const { data: events } = useEventSource(apiStreamUrl);

  if (isInitialDataLoading) {
    return <div>Loading initial data...</div>;
  }

  return (
    <div className="p-4">
      <h1 className="text-2xl mb-4">Event Stream</h1>
      <div className="flex flex-col gap-4">
        {events.map((event) => (
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
