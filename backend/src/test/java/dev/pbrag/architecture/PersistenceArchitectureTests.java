package dev.pbrag.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 可执行的持久层边界，避免约定只停留在 ADR 和代码评审中。 */
@AnalyzeClasses(packages = "dev.pbrag", importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceArchitectureTests {

	@ArchTest
	static final ArchRule business_code_must_not_use_direct_jdbc = noClasses()
			.that().resideOutsideOfPackage("..platform.persistence..")
			.should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
			.because("业务数据库访问必须通过 MyBatis Mapper，底层类型处理器是唯一基础设施例外");

	@ArchTest
	static final ArchRule controllers_must_not_access_mappers = noClasses()
			.that().resideInAPackage("..api..")
			.should().dependOnClassesThat().resideInAPackage("..mapper..")
			.because("Controller 只能调用 Service，事务和业务规则必须留在服务层");

	@ArchTest
	static final ArchRule domain_must_not_depend_on_application_layers = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage("..api..", "..service..", "..mapper..")
			.because("领域对象不能反向依赖接口、应用服务或持久层");
}
