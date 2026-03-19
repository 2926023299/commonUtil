# 仓库贡献指南

## 项目结构与模块组织
业务代码放在 `src/main/java`，测试代码放在 `src/test/java`，资源文件放在 `src/main/resources`。推荐按 Spring Boot 常见分层组织包结构，例如 `controller`、`service`、`repository` 或 `mapper`、`config`、`model`。若使用 MyBatis / MyBatis-Plus，XML 映射文件统一放在 `src/main/resources/mapper`。测试目录应与正式代码保持一致的包结构，便于定位和回归验证。若后续拆分多模块，保持模块职责单一，禁止循环依赖；可复用基础能力应下沉到独立公共模块。

## 构建、测试与开发命令
优先使用项目自带 Wrapper；若仓库未提供，再使用本机工具。

- `./mvnw clean verify` 或 `mvn clean verify`：完整构建、执行测试并校验产物。
- `./mvnw test` 或 `mvn test`：仅运行测试。
- `./gradlew build` 或 `gradle build`：Gradle 项目的完整构建。
- `./gradlew test` 或 `gradle test`：运行 Gradle 测试任务。

请在仓库根目录执行命令，避免生成文件散落到错误目录。

## 代码风格与命名约定
统一使用 4 个空格缩进，文件编码为 UTF-8。类名使用 `PascalCase`，方法和字段使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`，包名全部小写。Spring Boot 分层命名建议明确语义，例如 `UserController`、`OrderService`、`PaymentRepository`、`JwtProperties`。若使用 MyBatis，命名建议保持一致：实体用 `Entity` 或领域名本身，传输对象使用 `DTO`、`VO`、`BO` 后缀，数据访问接口统一使用 `*Mapper`。公共方法应明确输入、输出与异常边界，复杂行为补充简短 Javadoc；避免在 `controller` 中编写业务逻辑，事务边界优先放在 `service` 层。

## 测试规范
测试文件放在 `src/test/java` 下对应的包路径中，测试类命名采用 `*Test`。测试方法应直接表达行为，例如 `shouldReturnBadRequestWhenParamIsMissing`。优先区分单元测试与 Spring 集成测试：纯业务逻辑优先写单元测试，涉及 MVC、配置、数据库或事务时再使用 `@SpringBootTest`、`@WebMvcTest` 等切片测试。修复缺陷时，优先补充能稳定复现问题的测试。

## 代码审查重点
代码评审优先关注正确性、兼容性和可维护性，而不是表面风格。提交前请重点检查：

- 是否改变了现有公共 API 或默认行为
- 是否正确处理空值、异常、线程安全和并发访问
- 是否引入重复逻辑、隐式副作用或不必要依赖
- 是否把配置、事务、权限或参数校验放到了错误分层
- 是否存在 N+1 查询、全表更新、条件遗漏或动态 SQL 拼接风险
- 是否补齐了测试，且测试能覆盖回归风险

## Spring Boot 兼容约定
优先保持标准 Spring Boot 约定，减少“魔法式”封装。基础工具类应可脱离容器独立使用；与框架集成的能力放在独立配置类中。新增 Boot 相关代码时请遵循以下原则：

- 配置项集中到 `application.yml` 或 `application-*.yml`，并使用 `@ConfigurationProperties` 管理
- 自动装配、Bean 注册和条件装配放在 `config` 包，不要散落在业务类中
- 参数校验使用 `jakarta.validation` 或 Spring Validation，不在控制器中手写重复校验
- 异常处理优先通过统一异常处理器收口，避免控制器中重复 `try/catch`
- 数据访问层仅负责持久化，事务与聚合逻辑放在 `service` 层

## MyBatis / MyBatis-Plus 约定
使用 MyBatis / MyBatis-Plus 时，优先保证分层清晰和 SQL 可维护性，不把 ORM 细节泄漏到控制层。建议遵循以下规范：

- `Mapper` 接口只负责数据访问，不承载业务编排
- 简单 CRUD 优先使用 MyBatis-Plus 标准能力，复杂查询再下沉到自定义 SQL
- XML SQL 语句显式列出字段，避免直接使用 `select *`
- 动态 SQL 必须保证条件完整，防止误删、误更新和全表操作
- 分页统一使用项目约定的分页对象，不在业务代码中手写分页参数拼接
- 实体对象与接口出参分离，禁止将数据库实体直接作为对外 `VO`

若引入代码生成，生成物也必须二次审查。重点检查字段类型、索引字段查询、逻辑删除、乐观锁、审计字段和批量操作是否符合业务要求，不要直接无差别提交生成代码。

## 提交、PR 与 Agent 协作
提交信息保持简短、使用祈使句，并聚焦单一变更，例如 `add string normalization helper`、`fix null handling in date utils`。PR 至少应包含问题背景、解决方案、影响范围和测试依据。

Agent 或贡献者修改 Spring Boot 或 MyBatis 相关代码时，应明确说明影响层级，例如控制器、服务、配置、Mapper 或 XML。若改动接口、配置项、启动行为、自动装配逻辑或 SQL 映射，必须说明兼容性影响，并同步更新测试、文档和示例配置。

## 配置与安全
不要提交 IDE 配置、本地密钥或构建产物。可复用配置应纳入版本控制，机器相关或本地覆盖配置应排除在仓库之外。
