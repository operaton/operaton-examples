# AI Agent Guidelines — operaton-examples

You are working in a curated catalog of Operaton example projects. Quality
bar: every example must be bullet-proof — building it means testing its
processes against real integrations. Read `docs/EXAMPLE_STANDARDS.md` before
writing anything; it is the binding definition of done.

## Non-negotiable rules

1. **Standards first.** `docs/EXAMPLE_STANDARDS.md` overrides your defaults.
   If a request conflicts with it, surface the conflict instead of silently
   deviating.
2. **Reference example.** `examples/01-getting-started` is the canonical
   shape. When in doubt about structure, build files, test style, README
   layout, or BPMN conventions — mirror it.
3. **Operaton, not Camunda.** Dependencies are `org.operaton.*`; BPMN/DMN
   extension namespace is `http://operaton.org/schema/1.0/bpmn` with the
   `operaton:` prefix. When porting from Camunda 7 examples, translate every
   `camunda` occurrence; grep for `camunda` before finishing — the result
   must be empty.
4. **Dual build parity.** Any dependency or version change must be applied to
   BOTH `pom.xml` and `build.gradle.kts`, then verified with BOTH
   `./mvnw verify` and `./gradlew build`. Never claim success without having
   run both.
5. **Testcontainers, real systems.** Integration tests start PostgreSQL and
   every integrated external system as containers. Never substitute H2 or an
   in-process fake for the system the example is about.
6. **TDD per example.** Write the failing integration test (deploy → run →
   assert end state) before implementing delegates/configuration.
7. **Minimalism.** Before finishing, actively remove: unused dependencies,
   dead code, gratuitous abstraction layers, configuration that restates
   defaults.
8. **Concept vs use-case scope.** Concept examples (`examples/NN-*`) demonstrate
   one primary Operaton concept. Use-case examples (`examples/use-cases/*`)
   demonstrate one business topic and may combine several concepts. Both must be
   minimal — no element that does not serve the demonstrated purpose.
9. **Evidence before claims.** Paste the tail of the passing build output in
   your summary. "Should work" is a failure state.

## Workflow for a new example

1. Read `docs/EXAMPLE_STANDARDS.md` and the reference example.
2. Check the Roadmap section of the repository plan (docs/superpowers/plans/)
   for the example's defined scope and acceptance criteria.
3. Copy the reference example's wrapper files and build-file skeletons;
   adjust artifactId/package.
4. Model the BPMN/DMN first (with DI), then write the failing IT, then
   implement.
5. Write README last, against the running example (commands you actually ran).
6. Run the full review checklist from EXAMPLE_STANDARDS.md §10.

## Pinned versions

Defined in the root README version table. Never bump a version in a single
example; version bumps are repo-wide changes touching all examples.
