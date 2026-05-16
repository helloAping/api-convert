# Code Style Rules

## Java version

- The project targets Java 25.
- Do not downgrade `pom.xml` to Java 21 only because a local default Maven uses JDK 21.
- On this machine, verify with OpenJDK 25 explicitly; the local JDK 25 path is stored in `AGENTS.local.md` as `JAVA_HOME_25`. If not set there, ask the user for the path and save it.

```bash
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q compile
JAVA_HOME="$JAVA_HOME_25" PATH="$JAVA_HOME_25/bin":$PATH mvn -q test
```

## Lombok

- Entity classes should use Lombok to generate getters and setters.
- Do not hand-write repetitive getter/setter methods in Entity classes.
- Prefer `@Getter` and `@Setter` on Entity classes.
- Avoid broad Lombok annotations such as `@Data` when equality, hashCode, or toString behavior could be surprising for persistence objects.
- Do not include secrets such as provider API keys in generated `toString()` output.

## 注释规则

- 每次新增或实质修改 Java/TypeScript 类、record、interface、公开方法/函数、非显而易见的字段/常量时，必须补充简洁的中文注释或 JavaDoc/JSDoc。
- 注释应说明业务目的、边界契约、安全约束或供应商特定行为。
- 不要添加复述语法的噪声注释；优先解释代码为什么存在、应该如何使用。
- 涉及请求/响应日志、凭证、API Key、token 或供应商错误时，注释必须明确说明脱敏或暴露边界。
