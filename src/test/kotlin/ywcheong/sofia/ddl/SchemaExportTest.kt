package ywcheong.sofia.ddl

import jakarta.persistence.EntityManagerFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.spi.MetadataImplementor
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.tool.hbm2ddl.SchemaExport
import org.hibernate.tool.schema.TargetType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.util.*
import kotlin.test.assertTrue

@SpringBootTest
@DisplayName("데이터베이스 DDL")
class SchemaExportTest {

    @Autowired
    lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    @DisplayName("데이터베이스 DDL - init.sql 생성")
    fun `데이터베이스 DDL - 생성`() {
        val sfi = entityManagerFactory.unwrap(SessionFactoryImplementor::class.java)
        val entityClasses = mutableSetOf<Class<*>>()
        sfi.runtimeMetamodels.mappingMetamodel.forEachEntityDescriptor { entityClasses.add(it.mappedClass) }
        val serviceRegistry = StandardServiceRegistryBuilder().applySettings(sfi.properties).build()

        try {
            val metadata = MetadataSources(serviceRegistry).apply { entityClasses.forEach { addAnnotatedClass(it) } }
                .buildMetadata() as MetadataImplementor
            val outputFile = File("./init.sql").also { if (it.exists()) it.delete() }

            SchemaExport().setOutputFile(outputFile.absolutePath).setDelimiter(";").setFormat(true)
                .createOnly(EnumSet.of(TargetType.SCRIPT), metadata)

            val sql = outputFile.readText()
            println(sql)
            assertTrue(outputFile.exists())
            assertTrue(sql.contains("create table"))

        } finally {
            StandardServiceRegistryBuilder.destroy(serviceRegistry)
        }
    }
}