package ywcheong.sofia.archunit

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import ywcheong.sofia.aspect.AvailableCondition

@DisplayName("Controller 어노테이션")
class ControllerAnnotationTest {

    companion object {
        private val allClasses: JavaClasses = ClassFileImporter()
            .importPackages("ywcheong.sofia")
    }

    @Nested
    @DisplayName("Controller 메서드 어노테이션 검증")
    inner class ControllerMethodAnnotationTests {

        @Test
        @DisplayName("모든 Controller 메서드는 @AvailableCondition을 가져야 한다")
        fun allControllerMethodsShouldHaveAvailableCondition() {
            methods()
                .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                .and().areDeclaredInClassesThat().haveNameNotMatching(".*Test.*")
                .and().arePublic()
                .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
                .should(haveAnnotations(AvailableCondition::class.java))
                .check(allClasses)
        }
    }

    private fun haveAnnotations(vararg annotationClasses: Class<out Annotation>): ArchCondition<JavaMethod> {
        val annotationNames = annotationClasses.joinToString(", ") { "@${it.simpleName}" }
        return object : ArchCondition<JavaMethod>("have annotations $annotationNames") {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val missingAnnotations = annotationClasses.filter { !method.isAnnotatedWith(it) }

                if (missingAnnotations.isNotEmpty()) {
                    val missingNames = missingAnnotations.joinToString(", ") { "@${it.simpleName}" }
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "${method.fullName}에 $missingNames 어노테이션이 없습니다"
                        )
                    )
                }
            }
        }
    }
}
