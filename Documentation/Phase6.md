# Phase 6 implementation evidence

Updated 2026-09-21. **Implementation delivered; acceptance remains open for the full image dependency scan/SBOM and a clean-checkout remote CI run.** Local regression, PostgreSQL, source scan, container OS scan, packaging and startup checks passed. This is foundation evidence, not production readiness.

## Baseline and design assessment

The unmodified application passed 62 tests on Java 21.0.7 and Maven 3.9.9 (Windows 11). H2 tests did not exercise Flyway. Maven's sandbox default repository was unwritable; a project-local ignored cache was used with filesystem/network approval. Docker Desktop was installed under the user's local Programs directory rather than on PATH.

Retain the modular monolith, controller/service separation, Java 21, JPA, SQL migrations and legacy API shape. Replace the implicit provider HTTP transport with an explicitly pooled, deadline-cancellable transport with bounded response bodies and no automatic retries. Repair orchestration to record every admitted inference outcome exactly once on a best-effort diagnostic basis. Diagnostic database failures must not replace a successful provider response or its original failure; financial accounting remains later work.

Upgraded the framework baseline to Spring Boot 4.1.1 using the official [Spring Boot migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) and [release information](https://spring.io/projects/spring-boot/). Adopted Jackson 3 and the new MVC test/Flyway modules rather than introducing a permanent compatibility layer. Overrode managed Tomcat 11.0.24 with 11.0.25 after scanning found CVE-2026-65182, CVE-2026-65905 and CVE-2026-68525. The [Tomcat security advisory](https://tomcat.apache.org/security-11.html#Fixed_in_Apache_Tomcat_11.0.25) identifies the fixed patch; remove the override when Boot manages it or a newer version. Trivy's critical severity classifications differ from the vendor's contextual ratings; no finding was suppressed on that basis.

No external consumer or production deployment is documented in this repository. Preserve the public inference shape while deliberately correcting erroneous status codes and making demo credentials explicit local bootstrap only. Preserve V1–V5 checksums; add forward migration and startup safeguards. Do not delete or reset existing databases.

## Implemented scope

| Work package | Delivered |
|---|---|
| G001 baseline/build | Wrapper 3.9.11 with verified SHA-256; Boot/Jackson upgrade; patched Tomcat; deterministic JAR timestamp; pinned image bases/actions; CI tests/scans/SBOM; release manifest |
| G002 database | Four real PostgreSQL integration checks covering V1–V6 fresh install, populated V5 upgrade, real application startup/private management, and rehashed demo rejection |
| G003 bootstrap | V6 deactivates demo key; startup guard; explicit local key/provider bootstrap; loopback Compose publication; private management; non-root image |
| G004 lifecycle/errors | Input/query/media/status repair; safe fixed responses and operational provider errors; one best-effort diagnostic call per inference outcome; diagnostic failures preserve original result |
| G005 bounds | Pre-authentication body/concurrency admission, pooled HTTP limits, typed connect/read/acquisition timeout handling, cancellable upstream deadline, no retries or redirects |
| G006 evidence | Bounded metric tags, controllable HTTP upstream, slow/error/saturation/privacy tests, opt-in benchmark and Phase 7 streaming experiment |

The implementation keeps the legacy request/success/error shapes; deliberate compatibility changes are safe fixed messages, provider-unavailable 503, validated bad queries, enforced body/media limits, disabled nonlocal demo authentication and management on port 9090. [Operations.md](Operations.md) covers upgrade and rollback limitations. Existing customer databases were not accessed; upgrade evidence used disposable schemas with representative rows.

## Observed verification

Source base revision: `cb95950a2d78431656cb6ca4756a0b056c20bdc0`, **dirty working tree with these Phase 6 changes**. No new commit or remote workflow run was created. Logs and detailed Maven reports are local ignored build outputs; durable benchmark data is in [evidence/phase6-baseline.json](evidence/phase6-baseline.json).

Environment: Windows 11, Java 21.0.7, Maven wrapper 3.9.11, Docker Desktop 4.90.0 / engine 29.7.2, PostgreSQL 16.15 (`postgres:16-alpine` pinned digest in CI). Runtime image: Temurin 21.0.12+8, Alpine 3.24.2, UID/GID 10001. Tests used a disposable PostgreSQL container on loopback port 15432 and random `p6_…` schemas; fixtures dropped only their own schemas. No existing database was reset.

| Check | Observed result |
|---|---|
| Original `mvn -B test` | 62 passed before implementation |
| Final `mvnw.cmd -B -Ppostgres-it clean verify` | 88 unit/MVC/transport tests passed; one opt-in benchmark skipped; four PostgreSQL integration tests passed; zero failures/errors |
| `mvnw.cmd -B -Dgateway.benchmark=true -Dtest=BaselineBenchmarkTest test` | One benchmark test passed separately; 320 successful mock HTTP calls including warm-up |
| Repeat `mvnw.cmd -B -o -DskipTests package` after clean build | Identical SHA-256 on both JARs |
| Packaged image | Built from exact tested JAR; container JAR SHA-256 matched; UID/GID 10001; real PostgreSQL readiness returned `{"status":"UP"}` |
| Source image | Built successfully with temporary `--add-host repo.maven.apache.org:104.18.19.12` for this host's Docker DNS issue; no IP override committed |
| Source security scan | Trivy 0.74.0: zero high/critical dependency, secret or configuration findings after Tomcat patch |
| Runtime OS scan | Trivy 0.74.0: 73 Alpine packages checked; zero high/critical findings |
| Full runtime dependency scan / SBOM | **Not verified locally**: Java index downloads from both official registries ended with unexpected EOF |
| GitHub Actions clean-checkout verification | **Not run remotely**; workflow configured to fail on test failures or high/critical findings and retain reports |

Final JAR SHA-256: `ae60015186ed6b34b557ca1d0d23fc05567293efc1d719aad26459f399505aa8` (64,614,319 bytes). Final packaged image ID: `sha256:c0017a487d066808cb90721745876856d319d40ce857aa7156ac01163c6ed1fd`. These identify a local uncommitted build, not a signed release. `scripts/release_manifest.py` writes source revision/dirty state, artifact hash/size and toolchain target to `target/release-manifest.json`.

The source scan used a complete local Maven repository mounted at `/root/.m2/repository`, `--offline-scan`, a vulnerability DB updated 2026-09-21T07:13Z, and Trivy's embedded configuration checks after a policy download failed. It excluded `.git`, `.build-cache`, and generated `target` output. The independent OS scan explicitly excluded JARs; it is not a substitute for the pending full image scan. CI retains the full source/JAR/image scans and has no vulnerability suppression or `continue-on-error` bypass.

### Reproduction

Use the commands in README with Java 21 and a disposable PostgreSQL database. This Windows run additionally set `MAVEN_USER_HOME` to the ignored `.build-cache/wrapper` and passed `"-Dmaven.repo.local=E:\ashish\Heisenberg\AI-Inference-Gateway\.build-cache\m2"`. Set `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, and `TEST_DATABASE_PASSWORD` for `-Ppostgres-it`; missing configuration fails rather than skips the gate. The test user needs permission to create/drop schemas in the designated test database.

The slow-response fixture trickles bytes every 20 ms, configures a 150 ms total deadline with a longer read timeout, checks bounded cancellation, then successfully reuses the single-connection pool. Other fixtures check pool-acquisition saturation without extra dispatch, expired deadline/no dispatch, 429/500/redirect/oversized provider responses without retry or raw text, request-body limits without Content-Length, permit release, parser/query errors, safe 403, diagnostic failure after success and failure, and management isolation on real listeners.

### Non-streaming baseline

The benchmark used loopback HTTP, synthetic Ollama output, a 1,024-character prompt, one BCrypt key, H2 in-process diagnostic writes, 20 gateway warm-up requests and 100 samples per measured case. Java saw 24 logical processors and a 4,215,275,520-byte maximum heap. It used the upgraded Boot baseline before the Tomcat security patch. No Redis, external provider latency or real model inference was involved.

| Case | Concurrency | p50 | p95 | p99 | Observed requests/s |
|---|---:|---:|---:|---:|---:|
| Direct mock | 1 | 0.55 ms | 1.13 ms | 1.97 ms | 1479.3 |
| Gateway | 1 | 51.79 ms | 60.24 ms | 64.85 ms | 18.8 |
| Gateway | 10 | 54.56 ms | 59.13 ms | 62.02 ms | 177.1 |

End-of-run used Java heap was 72,000,456 bytes, a snapshot rather than a peak-memory or leak measurement. Sequential gateway/direct p95 difference was approximately 59.11 ms; subtracting percentiles is only a comparison, not a paired per-request overhead distribution. The sequential gateway sample lasted approximately 5.3 seconds. This short closed workload does not validate the PRD's 100 requests/s, 200-stream, one-hour target or its ≤50 ms overhead goal. BCrypt verification and synchronous diagnostics are included; this experiment did not profile their individual contribution. CPU utilization, RSS, peak buffers and production PostgreSQL latency were not measured here.

## Remaining acceptance and operating limits

1. Run the committed workflow from a clean checkout; retain its exact revision, test reports and scan artifacts. No remote CI result is implied by local success.
2. Complete the full image dependency scan and container SBOM once the Java advisory database can be downloaded. Do not accept a partial scan or disable that gate to publish a release.
3. Recheck dependency/base-image advisories at release time. Pins provide repeatability, not permanent security; maintain them.

Immediate downstream disconnect cancellation, bounded stream writes, deployment-isolated pools, indexed identities, tenancy, durable usage/budgets, configuration refresh, recovery drills and production load/soak tests remain future phases. The current total deadline actively aborts upstream I/O but does not hard-limit all synchronous database work or downstream delivery. Local diagnostics can be dropped; they are not audit or billing records. No real Ollama/cloud-provider smoke test, full Compose model download, external consumer migration or production deployment was performed.

## Streaming experiment for Phase 7

Compare async MVC with a reactive edge using the same mock upstream: 100 requests/s, up to 200 simultaneous streams, 1 KiB input, 256 chunks of 64 bytes at 20 ms intervals, a 1% mid-stream failure mix, and 10% slow/disconnecting clients. Publish JVM/hardware/network, executor/pool sizes, CPU/RSS, added TTFT, cleanup latency, error rate and peak buffered bytes. Run a one-hour steady load after a warm-up and a separate saturation ramp. The Phase 6 non-streaming measurements are not evidence for this streaming target.
