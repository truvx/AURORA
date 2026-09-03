export const TOOLS = [
  {
    name: "searchMusic",
    description: "Search for music candidates based on a natural language query or filters.",
    parameters: {
      type: "object",
      properties: {
        query: { type: "string", description: "The search query (e.g., 'Malayalam driving songs')." },
        mood: { type: "string", description: "The desired mood (e.g., 'dark', 'energetic')." },
        energy: { type: "string", description: "The desired energy level ('low', 'medium', 'high')." },
        era: { type: "string", description: "The time period (e.g., '2010s')." },
        language: { type: "string", description: "The language of the songs." },
      },
    },
  },
  {
    name: "findSimilarMusic",
    description: "Find songs similar to a reference track.",
    parameters: {
      type: "object",
      properties: {
        referenceTrackId: { type: "string", description: "The resolved item ID (e.g. youtube:123 or local:456) to find similar music for." },
        mood: { type: "string", description: "Any additional mood constraints." },
      },
      required: ["referenceTrackId"],
    },
  },
  {
    name: "playTrack",
    description: "Propose to play a specific track by its resolved ID.",
    parameters: {
      type: "object",
      properties: {
        trackId: { type: "string", description: "The resolved item ID of the track to play." },
      },
      required: ["trackId"],
    },
  },
  {
    name: "addToQueue",
    description: "Propose to add specific tracks to the queue.",
    parameters: {
      type: "object",
      properties: {
        trackIds: {
          type: "array",
          items: { type: "string" },
          description: "List of resolved item IDs to add to the queue."
        },
      },
      required: ["trackIds"],
    },
  },
];
