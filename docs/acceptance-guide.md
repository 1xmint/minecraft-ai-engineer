# Manual signed-in game acceptance

This developer flow connects one explicitly chosen synthetic GitHub Minecraft
run with the operator's existing local Codex sign-in. It is separate from normal
CI, which never invokes Codex. The workflows must first be merged onto the default
branch; their publication alone does not prove the combined flow works.

## Run it

Use the repository owner's GitHub CLI session and a clean reviewed checkout of
this repository. Install the pinned Node dependencies. Keep the existing Codex
ChatGPT sign-in local; no API key or login file is uploaded.

1. Start `live-acceptance.yml` manually on the reviewed ref, for example
   `gh workflow run live-acceptance.yml --ref main`.
2. Read its run ID, source SHA and attempt using GitHub's run page or
   `gh api repos/1xmint/minecraft-ai-engineer/actions/runs/RUN_ID`.
   Use a clean local checkout at that exact SHA.
3. Once its request artifact is available, run:

   ```text
   node scripts/acceptance/respond.mjs --run RUN_ID --attempt ATTEMPT --sha REVIEWED_SHA --model gpt-6-luna
   ```

   The command verifies source and GitHub provenance, compiles that reviewed
   TypeScript, reconstructs the allowed request, then makes one isolated SDK call.
   It saves the validated answer privately before dispatching the response workflow.

4. Inspect the request workflow's final status and `live-game-acceptance-evidence`
   artifact. Both the model-design marker and actual client construction,
   delegation, conflict, truth-table and service/game restart proofs must pass.
   Compare request/result digests and the provider receipt; a model response alone
   is not functional evidence.

The request expires after 15 minutes. Only the fixed synthetic lamp scenario is
supported. GitHub receives bounded fixture IDs, a validated plan and minimal usage
metadata, never personal credentials, SDK events or real project context. Normal
server permissions, immutable context checks and proposal validation still apply.
A successful lamp flow does not establish general design generation or complete
phase 1.

## Interrupted publication

A durable started receipt prevents a second SDK call for the same request. If the
answer was saved but publication failed, use the printed response path with the
same explicit run, attempt, SHA and model:

```text
node scripts/acceptance/respond.mjs --run RUN_ID --attempt ATTEMPT --sha REVIEWED_SHA --model gpt-6-luna --publish SAVED_RESPONSE_FILE
```

This only republishes the validated saved answer while its original run/context
remains live. It never invokes the model again. An expired run needs a new manual
run. No automatic model fallback or retry is performed.
