# Bundled build-time dependencies

This directory contains only compile-time Java ME/M3G API stubs and the
ProGuard 5.3.3 jar needed by the build scripts. They are not packaged into the
MIDlets. The JDK itself is intentionally not bundled; install JDK 8 and set
`JAVA_HOME` (or put its `javac` on `PATH`).

Before redistributing this source package, preserve the applicable license and
notice files for the bundled ProGuard and API stubs.
