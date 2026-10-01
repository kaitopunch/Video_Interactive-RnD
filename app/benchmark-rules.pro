# The `benchmark` build type only (LLM.md §10): shrunk and optimised like release, but not renamed.
# BaselineProfileGenerator records the class and method names it sees; under obfuscation it would write
# names like `a.b` into app/src/main/baseline-prof.txt, which match nothing and compile nothing ahead of time.
-dontobfuscate
