import OpenAI from "openai";
import { TOOLS } from "./tools";

export async function processWithOpenAI(apiKey: string, query: string, context: any) {
  const openai = new OpenAI({ apiKey });

  const systemInstruction = `You are AURORA, a music intelligence agent.
You help users find music, build queues, and play tracks.
Do NOT fabricate track IDs or URLs. Use the provided tools to search or interact.
If you are asked to play or add to queue, ensure you have the actual track ID. If you don't have it, search for it first.
Context:
${JSON.stringify(context)}
`;

  try {
    const response = await openai.chat.completions.create({
      model: "gpt-4o",
      messages: [
        { role: "system", content: systemInstruction },
        { role: "user", content: query }
      ],
      tools: TOOLS.map(t => ({
        type: "function",
        function: {
          name: t.name,
          description: t.description,
          parameters: t.parameters as any
        }
      })),
      temperature: 0.2,
    });

    const message = response.choices[0].message;
    const calls = message.tool_calls;

    if (calls && calls.length > 0) {
      return {
        toolCalls: calls.map((call: any) => ({
          name: call.function.name,
          args: JSON.parse(call.function.arguments)
        })),
        text: message.content || ""
      };
    }

    return {
      text: message.content || "",
      toolCalls: []
    };
  } catch (error) {
    console.error("OpenAI API Error:", error);
    throw new Error("Provider unavailable or failed.");
  }
}
