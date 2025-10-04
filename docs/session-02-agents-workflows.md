# Session 2 — Agents & Workflows with VoyagerMate

**Duration**: 4.5 hours  
**Focus**: Travel-planning workflows, agent patterns, and orchestration strategies using Spring AI and Spring Shell.

---

## 1. Recap & Objectives (15 min)
- Review Session 1 achievements (text/image, optional audio), tools, itineraries.
- Set the stage for orchestrating multi-step planning experiences.

## 2. Workflow vs Agent Primer (30 min)
- Define deterministic workflows vs goal-seeking agents in travel scenarios.
- Map to Spring AI capabilities: sequential ChatClient calls vs tool-enabled prompts.
- Introduce `ItineraryWorkflowService` and the `workflow` shell command as the case study.

## 3. Sequential Workflow Deep Dive (60 min)
- Inspect `ItineraryWorkflowService.orchestrate`:
  - Discovery → Itinerary Draft → Risk Review → Next Steps.
  - Each step runs as an independent prompt for traceability.
- Discuss how to persist intermediate state (deferred to Session 3).

### Demo — Workflow Summary
```bash
workflow --name Mira --origin Amsterdam --destination Tokyo \
         --depart 2025-04-10 --return 2025-04-20 \
         --budget balanced --interests food,design,nightlife
```
- Review the JSON output and ideate how a UI might consume each section.

### Lab — Enhancing the Workflow
- Add a parallel branch that generates alternative lodging options.
- Merge the branch back into `TripWorkflowSummary` and expose it in the command output.

## 4. Agentic Behaviours (45 min)
- Demonstrate `plan-itinerary` combining tools + structured output.
- Experiment with prompts that encourage VoyagerMate to choose specific tools from `VoyagerTools`.
- Discuss agent guardrails without relying on Advisor API (to be covered later).

### Lab — Travel Concierge Agent
1. Add a tool method that checks loyalty program benefits.
2. Update the system prompt so VoyagerMate recommends upgrades when budget allows.
3. Capture the tool outputs in the itinerary response and display them via the shell command.

## 5. Observability & Operational Notes (30 min)
- Highlight latency tracking printed in `chat`, `describe-image`, and (when enabled) `transcribe-audio` commands.
- Brainstorm metrics/events to add in future sessions (token usage, tool telemetry).
- Note placeholders for Postgres/Rancher Desktop introduction (Session 3 teaser).

## 6. Capstone Challenge (40 min)
- Teams design a concierge flow using shell commands:
  - Receive traveller voice note → summarise with `transcribe-audio` (skip or use a provided transcript if no transcription deployment is available).
  - Produce itinerary with `plan-itinerary`.
  - Run `workflow` to capture risks and next actions.
- Present strategies for UI integration and state hand-off.

## 7. Closing (10 min)
- Gather feedback and align on readiness for Session 3 (data integration) & Session 4 (validation).

---

## Additional Resources
- [Spring AI GitHub](https://github.com/spring-projects/spring-ai)
- [Spring AI Examples](https://github.com/spring-projects/spring-ai/tree/main/spring-ai-examples)
- [Spring One Talks on AI](https://spring.io/videos)
- Community: GitHub Discussions, StackOverflow `spring-ai`, Spring Blog.
