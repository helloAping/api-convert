# Testing Rules

- Verify Java code with OpenJDK 25.
- Minimum verification before handing off code changes:

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q compile
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q test
```

- If a local default Maven uses JDK 21 and fails with `不支持发行版本 25`, use the JDK 25 path from `AGENTS.local.md` instead of changing the project target.
- If `AGENTS.local.md` does not contain `JAVA_HOME_25`, ask the user for the JDK 25 installation path and save it there.
