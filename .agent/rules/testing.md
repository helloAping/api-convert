# Testing Rules

- Verify Java code with OpenJDK 25.
- Minimum verification before handing off code changes:

```bash
JAVA_HOME='D:\develop\java\openjdk-25' PATH='D:\develop\java\openjdk-25\bin':$PATH mvn -q compile
JAVA_HOME='D:\develop\java\openjdk-25' PATH='D:\develop\java\openjdk-25\bin':$PATH mvn -q test
```

- If a local default Maven uses JDK 21 and fails with `不支持发行版本 25`, switch `JAVA_HOME` instead of changing the project target.
