package com.dbflow5.processor.definition

import com.dbflow5.annotation.Column
import com.dbflow5.annotation.ColumnMap
import com.dbflow5.annotation.ModelView
import com.dbflow5.annotation.ModelViewQuery
import com.dbflow5.processor.ClassNames
import com.dbflow5.processor.ColumnValidator
import com.dbflow5.processor.ProcessorManager
import com.dbflow5.processor.definition.behavior.AssociationalBehavior
import com.dbflow5.processor.definition.behavior.CreationQueryBehavior
import com.dbflow5.processor.definition.behavior.CursorHandlingBehavior
import com.dbflow5.processor.definition.column.ColumnDefinition
import com.dbflow5.processor.utils.ElementUtility
import com.dbflow5.processor.utils.`override fun`
import com.dbflow5.processor.utils.annotation
import com.dbflow5.processor.utils.ensureVisibleStatic
import com.dbflow5.processor.utils.extractTypeNameFromAnnotation
import com.dbflow5.processor.utils.implementsClass
import com.dbflow5.processor.utils.isNullOrEmpty
import com.dbflow5.processor.utils.simpleString
import com.dbflow5.processor.utils.toTypeElement
import com.dbflow5.processor.utils.toTypeErasedElement
import com.dbflow5.quoteIfNeeded
import com.grosner.kpoet.S
import com.grosner.kpoet.`=`
import com.grosner.kpoet.`public static final field`
import com.grosner.kpoet.`return`
import com.grosner.kpoet.final
import com.grosner.kpoet.modifiers
import com.grosner.kpoet.public
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * Description: Used in writing ModelViewAdapters
 */
class ModelViewDefinition(modelView: ModelView,
                          manager: ProcessorManager,
                          element: TypeElement)
    : EntityDefinition(element, manager) {

    private var queryFieldName: String? = null

    override val methods: Array<MethodDefinition> = arrayOf(
        LoadFromCursorMethod(this),
        ExistenceMethod(this),
        PrimaryConditionMethod(this))

    private val creationQueryBehavior = CreationQueryBehavior(createWithDatabase = modelView.createWithDatabase)

    val priority = modelView.priority

    init {
        setOutputClassName("_ViewTable")
    }

    override val associationalBehavior = AssociationalBehavior(
        name = if (modelView.name.isNullOrEmpty()) modelClassName else modelView.name,
        databaseTypeName = modelView.extractTypeNameFromAnnotation { it.database },
        allFields = modelView.allFields
    )

    override val cursorHandlingBehavior = CursorHandlingBehavior(
        orderedCursorLookup = modelView.orderedCursorLookUp,
        assignDefaultValuesFromCursor = modelView.assignDefaultValuesFromCursor)

    override fun prepareForWriteInternal() {
        queryFieldName = null
        typeElement?.let { createColumnDefinitions(it) }
    }

    override fun createColumnDefinitions(typeElement: TypeElement) {
        val columnGenerator = BasicColumnGenerator(manager)
        val variableElements = ElementUtility.getAllElements(typeElement, manager)
        for (element in variableElements) {
            classElementLookUpMap[element.simpleName.toString()] = element
        }

        val columnValidator = ColumnValidator()
        for (variableElement in variableElements) {

            val isValidAllFields = ElementUtility.isValidAllFields(associationalBehavior.allFields, variableElement)
            val isColumnMap = variableElement.annotation<ColumnMap>() != null

            if (variableElement.annotation<Column>() != null || isValidAllFields
                || isColumnMap) {

                // package private, will generate helper
                val isPackagePrivate = ElementUtility.isPackagePrivate(variableElement)
                columnGenerator.generate(variableElement, this)?.let { columnDefinition ->
                    if (columnValidator.validate(manager, columnDefinition)) {
                        columnDefinitions.add(columnDefinition)
                        if (isPackagePrivate) {
                            packagePrivateList.add(columnDefinition)
                        }
                    }

                    if (columnDefinition.type.isPrimaryField) {
                        manager.logError("ModelView $elementName cannot have primary keys")
                    }
                }
            } else if (variableElement.annotation<ModelViewQuery>() != null) {
                if (!queryFieldName.isNullOrEmpty()) {
                    manager.logError("Found duplicate queryField name: $queryFieldName for $elementClassName")
                }

                val element = variableElement.toTypeErasedElement() as? ExecutableElement
                if (element != null) {
                    val returnElement = element.returnType.toTypeElement()
                    ensureVisibleStatic(element, typeElement, "ModelViewQuery")
                    if (!returnElement.implementsClass(manager.processingEnvironment, com.dbflow5.processor.ClassNames.QUERY)) {
                        manager.logError("The function ${variableElement.simpleName} must return ${com.dbflow5.processor.ClassNames.QUERY} from $elementName")
                    }
                }

                queryFieldName = variableElement.simpleString
            }
        }

        if (queryFieldName.isNullOrEmpty()) {
            manager.logError("$elementClassName is missing the @ModelViewQuery field.")
        }
    }

    override val primaryColumnDefinitions: List<ColumnDefinition>
        get() = columnDefinitions

    override val extendsClass: TypeName?
        get() = ParameterizedTypeName.get(com.dbflow5.processor.ClassNames.MODEL_VIEW_ADAPTER, elementClassName)

    override fun onWriteDefinition(typeBuilder: TypeSpec.Builder) {
        typeBuilder.apply {
            `public static final field`(String::class, "VIEW_NAME") { `=`(associationalBehavior.name.S) }

            elementClassName?.let { elementClassName ->
                columnDefinitions.forEach { it.addPropertyDefinition(typeBuilder, elementClassName) }
            }

            this.writeConstructor()

            writeGetModelClass(typeBuilder, elementClassName)

            creationQueryBehavior.addToType(this)

            `override fun`(String::class, "getCreationQuery") {
                modifiers(public, final)
                `return`("\"CREATE VIEW IF NOT EXISTS ${associationalBehavior.name.quoteIfNeeded()} AS \" + \$T.\$L().getQuery()", elementClassName, queryFieldName)
            }
            associationalBehavior.writeName(typeBuilder)

            `override fun`(ClassNames.OBJECT_TYPE, "getType") {
                modifiers(public, final)
                `return`("\$T.View", ClassNames.OBJECT_TYPE)
            }
        }

        methods.mapNotNull { it.methodSpec }
            .forEach { typeBuilder.addMethod(it) }
    }

}