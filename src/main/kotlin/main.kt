import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File
import java.io.FileReader
import java.nio.file.Files.createDirectories
import java.nio.file.Path

object TypeConstants {
    const val TEXT = "Text"
    const val NUMBER = "Number"
    const val DECIMAL = "Decimal"
    const val BOOLEAN = "Boolean"
    const val FORMULA = "Formula"
    const val DATE = "Date"
    const val TIMESTAMP = "Timestamp"
    const val TIME = "Time"
}

val primitiveTypes = listOf(
    TypeConstants.TEXT,
    TypeConstants.NUMBER,
    TypeConstants.DECIMAL,
    TypeConstants.BOOLEAN,
    TypeConstants.DATE,
    TypeConstants.TIMESTAMP,
    TypeConstants.TIME
)

object KeyConstants {
    const val KEY_TYPE = "type"
//    const val ORDER = "order"

    //    const val DEFAULT = "default"
    const val FORMULA_EXPRESSION = "expression"
    const val FORMULA_RETURN_TYPE = "returnType"
//    const val VALUE = "value"
}

val gson: Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
val types: JsonObject = gson.fromJson(FileReader("src/main/resources/types.json"), JsonObject::class.java)

fun getVariablesTs(): String {
    return """
        import { immerable } from 'immer'
        import { NonPrimitiveType } from './types'
        import { 
    """.trimIndent() +
            types.keySet().joinToString(separator = ", ") { typeName -> "${typeName}Row" } + " } from './rows'\n" + """
        export type Text = string
        export type Number = number
        export type Decimal = number
        export type Boolean = boolean
        export type Date = number
        export type Timestamp = number
        export type Time = number
        
        export type Variable =
    """.trimIndent() +
            "\n" + types.keySet()
        .joinToString(separator = "\n") { typeName -> "    | ${typeName}Variable" } + "\n\nexport type VariableId =" +
            "\n" + types.keySet().joinToString(separator = "\n") { typeName -> "    | $typeName" } +
            "\n\n" + types.entrySet().joinToString(separator = "\n") { (typeName, typeDef) ->
        """
                export class $typeName {
                    constructor(private id: number) { }

                    equals(other: ${typeName}): boolean {
                        if (!other) {
                            return false;
                        }
                        return this.id === other.id
                    }

                    hashCode(): number {
                        return this.id
                    }

                    toString(): string {
                        return String(this.id)
                    }
                }
            """.trimIndent() +
                "\n\n" + """
                    export class ${typeName}Variable {
                        [immerable] = true
                        readonly typeName = '${typeName}'
                        readonly id: $typeName
                        values: {${
            if (typeDef.asJsonObject.get("keys").asJsonObject.keySet()
                    .isEmpty()
            ) "" else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = "\n") { (keyName, keyDef) ->
                    "                            $keyName: ${when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                        TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                            else -> keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString
                        }
                        else -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString
                    }}"
                } + " \n"
        }                        }

                        constructor(id: number, values: { ${
            if (typeDef.asJsonObject.get("keys").asJsonObject.keySet()
                    .isEmpty()
            ) "" else typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = ", ") { (keyName, keyDef) -> "$keyName: ${when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                    TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                        else -> keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString
                    }
                    else -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString
                }}" }
        } }) {
                            this.id = new ${typeName}(id)
                            this.values = values
                        }

                        equals(other: ${typeName}Variable): boolean {
                            if (!other) {
                                return false;
                            }
                            return ${
            if (typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.keySet()
                    .isEmpty()
            ) "this.id.equals(other.id)"
            else {
                "(" + typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.entrySet()
                    .joinToString(separator = ") || (") { (_, keyNames) ->
                        keyNames.asJsonArray.map { it.asString }.joinToString(separator = " && ") { keyName ->
                            when (typeDef.asJsonObject.get("keys").asJsonObject.get(keyName).asJsonObject.get(
                                KeyConstants.KEY_TYPE
                            ).asString) {
                                in primitiveTypes -> "this.values.$keyName === other.values.$keyName"
                                TypeConstants.FORMULA -> "this.values.$keyName === other.values.$keyName"
                                else -> "this.values.${keyName}.equals(other.values.${keyName})"
                            }
                        }
                    } + ")"
            }
        }
                        }

                        hashCode(): number {
                            return this.id.hashCode()
                        }

                        toString(): string {
                            return JSON.stringify(this, null, 2)
                        }

                        toRow(): ${typeName}Row {
                            return new ${typeName}Row(this.id.hashCode(), {${
            if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
            else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = ",\n") { (keyName, keyDef) ->
                    if (primitiveTypes.contains(keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) || keyDef.asJsonObject.get(
                            KeyConstants.KEY_TYPE
                        ).asString == TypeConstants.FORMULA
                    )
                        "                              $keyName: this.values.${keyName}"
                    else "                              $keyName: this.values.${keyName}.hashCode()"
                } + " \n"
        }                            })
                        }
                    }
                """.trimIndent() + "\n"
    }
}

fun getVariablesTsReplaceVariable(): String {
    return """
        export function replaceVariable(typeName: NonPrimitiveType, id: number, values: object) {
            switch (typeName) {
                ${
        types.entrySet().joinToString(separator = "\n") { (typeName, typeDef) ->
            """
                    case '${typeName}': {
                        return new ${typeName}Variable(id, {${
                if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) {
                    ""
                } else {
                    "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                        .joinToString(separator = ",\n") { (keyName, keyDef) ->
                            when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                                TypeConstants.TEXT -> "                              $keyName:  String(values['${keyName}'])"
                                TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "                              $keyName:  parseInt(String(values['${keyName}']))"
                                TypeConstants.DECIMAL -> "                              $keyName:  parseFloat(String(values['${keyName}']))"
                                TypeConstants.BOOLEAN -> "                              $keyName:  Boolean(String(values['${keyName}'])).valueOf()"
                                in primitiveTypes -> "                              $keyName: this.values.${keyName}"
                                TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                    TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "                              $keyName:  parseInt(String(values['${keyName}']))"
                                    TypeConstants.DECIMAL -> "                              $keyName:  parseFloat(String(values['${keyName}']))"
                                    TypeConstants.BOOLEAN -> "                              $keyName:  Boolean(String(values['${keyName}'])).valueOf()"
                                    else -> "                              $keyName:  String(values['${keyName}'])"
                                }
                                else -> "                              $keyName: new ${
                                    keyDef.asJsonObject.get(
                                        KeyConstants.KEY_TYPE
                                    ).asString
                                }(parseInt(String(values['${keyName}'])))"
                            }
                        } + "\n                        "
                }
            }})
                    }
                """.trimIndent()
        }
    }
            }
        }
    """
}

fun getTypesTs(): String {
    return """
        import { LispExpression } from "./lisp"

        export type KeyType = PrimitiveType | NonPrimitiveType

        export type PrimitiveType =
            | 'Text'
            | 'Number'
            | 'Decimal'
            | 'Boolean'
            | 'Date'
            | 'Timestamp'
            | 'Time'
        // | 'Formula'
        // | 'Blob'

        export type NonPrimitiveType =
    """.trimIndent() + "\n" + types.keySet().joinToString(separator = "\n") { "    | '$it'" } + "\n\n" + """
        export type Key = {
            order: number
            name: string
            type: PrimitiveType
        } | {
            order: number
            name: string
            type: 'Formula'
            returnType: PrimitiveType
            expression: LispExpression
        }

        export type Type = {
            name: string
            url?: string,
            recursive: boolean
            keys: Record<string, Key>
        }

        export const types = {${
        "\n" + types.entrySet().joinToString(separator = ",\n") { (typeName, typeDef) ->
            """
                ${typeName}: {
                    name: '${typeName} ID',
                    url: '${
                "${
                    typeName.subSequence(0, 1).toString().toLowerCase()
                }${typeName.substring(1)}".split("(?=\\p{Upper})".toRegex()).joinToString(separator = "-") {
                    "${
                        it.subSequence(0, 1).toString().toLowerCase()
                    }${it.substring(1)}"
                }
            }',
                    recursive: ${
                if (typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                        .any { (_, keyDef) -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString == typeName }
                ) "true" else "false"
            },
                    keys: {${
                "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet().mapIndexed { index, (keyName, keyDef) ->
                    """                         ${keyName}: {
                            order: ${index},
                            name: '${keyDef.asJsonObject.get("name").asString}',
                            type: '${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}'${
                        if (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString != TypeConstants.FORMULA) "" else {
                            ",\n" + """                            returnType: '${keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString}',
                            expression: ${keyDef.asJsonObject.get(KeyConstants.FORMULA_EXPRESSION).asJsonObject}"""
                        }
                    }
                         }"""
                }.joinToString(separator = ",\n") + "\n"
            }                    },
                    uniqueConstraints: {${
                if (typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.keySet().isEmpty()) ""
                else "\n" + typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.entrySet()
                    .joinToString(separator = ",\n") { (ucName, keyNames) ->
                        "                       '$ucName': [${
                            keyNames.asJsonArray.joinToString(separator = ", ") { "'${it.asString}'" }
                        }]"
                    } + "\n                    "
            }},
                    assertions: {},
                    lists: {${
                if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
                else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.entrySet()
                    .joinToString(separator = ",\n") { (refTypeName, refTypeKey) ->
                        "                       $refTypeName: '${refTypeKey.asString}'"
                    } + "\n                    "
                    }}
                }
        """.trimIndent()
        }
    }}
    """.trimIndent()
}

fun getRowsTs(): String {
    return """
        import { immerable, Immutable } from 'immer'
        import { HashSet } from 'prelude-ts'
        import { DiffVariable } from './layers'
        import { ${types.keySet().joinToString(separator = ", ") { "${it}, ${it}Variable" }}} from './variables'
        
        export type Row =
    """.trimIndent() + "\n" + types.keySet().joinToString(separator = "\n") { "    | ${it}Row" } +
            "\n" + types.entrySet().joinToString(separator = "\n\n") { (typeName, typeDef) ->
        """
                export class ${typeName}Row {
                    readonly typeName = '${typeName}'
                    readonly id: number${
            if (typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.keySet().isEmpty()) ""
            else {
                "\n" + typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.entrySet().flatMap { (_, keyNames) ->
                    keyNames.asJsonArray.map {
                        "                    readonly ${it.asString}: ${
                            when (typeDef.asJsonObject.get("keys").asJsonObject.get(it.asString).asJsonObject.get(
                                KeyConstants.KEY_TYPE
                            ).asString) {
                                TypeConstants.TEXT -> "string"
                                TypeConstants.NUMBER, TypeConstants.DECIMAL, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                                TypeConstants.BOOLEAN -> "boolean"
                                TypeConstants.FORMULA -> when (typeDef.asJsonObject.get("keys").asJsonObject.get(it.asString).asJsonObject.get(
                                    KeyConstants.FORMULA_RETURN_TYPE
                                ).asString) {
                                    TypeConstants.TEXT -> "string"
                                    TypeConstants.NUMBER, TypeConstants.DECIMAL, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                                    TypeConstants.BOOLEAN -> "boolean"
                                    else -> "number"
                                }
                                else -> "number"
                            }
                        }"
                    }
                }.toSet().joinToString(separator = "\n")
            }
        }
                    values: {${
            if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
            else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = "\n") { (keyName, keyDef) ->
                    "                        $keyName: ${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> "string"
                            TypeConstants.NUMBER, TypeConstants.DECIMAL, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                            TypeConstants.BOOLEAN -> "boolean"
                            TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                TypeConstants.TEXT -> "string"
                                TypeConstants.NUMBER, TypeConstants.DECIMAL, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                                TypeConstants.BOOLEAN -> "boolean"
                                else -> "number"
                            }
                            else -> "number"
                        }
                    }"
                } + " \n                    "
        }}

                    constructor(id: number, values: { ${
            if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
            else typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = ", ") { (keyName, keyDef) ->
                    "$keyName: ${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> "string"
                            TypeConstants.NUMBER, TypeConstants.DECIMAL, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                            TypeConstants.BOOLEAN -> "boolean"
                            TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                TypeConstants.TEXT -> "string"
                                TypeConstants.NUMBER, TypeConstants.DECIMAL, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                                TypeConstants.BOOLEAN -> "boolean"
                                else -> "number"
                            }
                            else -> "number"
                        }
                    }"
                }
        } }) {
                        this.id = id
                        this.values = values${
            if (typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.keySet().isEmpty()) ""
            else {
                "\n" + typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.entrySet()
                    .flatMap { (_, keyNames) -> keyNames.asJsonArray.map { "                        this.${it.asString} = values.${it.asString}" } }
                    .toSet().joinToString(separator = "\n")
            }
        }
                    }

                    static toVariable(row: ${typeName}Row): ${typeName}Variable {
                        return new ${typeName}Variable(row.id, {${
            if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
            else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = ",\n") { (keyName, keyDef) ->
                    "                            ${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> "${keyName}: row.values.${keyName}"
                            TypeConstants.NUMBER, TypeConstants.DECIMAL, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "${keyName}: row.values.${keyName}"
                            TypeConstants.BOOLEAN -> "${keyName}: row.values.${keyName}"
                            TypeConstants.FORMULA -> "${keyName}: row.values.${keyName}"
                            else -> "${keyName}: new ${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}(row.values.${keyName})"
                        }
                    }"
                } + "\n                        "
        }})
                    }
                }
            """.trimIndent()
    }
}

fun getRowsTsDiffRow(): String {
    return """
      export class DiffRow {
        readonly id?: number
        active: boolean
        variables: {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = ",\n") {
            """            ${it}: {
                replace: Array<${it}Row>
                remove: Array<number>
            }"""
        } + "\n        "
    }}
        
        constructor(variables: {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = ",\n") {
            """            ${it}: {
                replace: Array<${it}Row>
                remove: Array<number>
            }"""
        } + "\n        "
    }}) {
            this.active = true
            this.variables = variables
        }

        static toVariable(diff: Immutable<DiffRow>): DiffVariable {
            return new DiffVariable(diff.id, diff.active, {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = ",\n") {
            """                ${it}: {
                  replace: HashSet.of<${it}Variable>().addAll(diff.variables.${it}.replace.map(x => ${it}Row.toVariable(x))),
                  remove: HashSet.of<${it}>().addAll(diff.variables.${it}.remove.map(x => new ${it}(x)))
                }"""
        } + "\n            "
    }})
        }
      }
    """.trimIndent()
}

fun getMutationTs(): String {
    return """import { Immutable } from 'immer';
import { NonPrimitiveType } from './types';
import { when } from './utils';
import { DiffVariable, getRemoveVariableDiff, getReplaceVariableDiff, getVariable, mergeDiffs } from './layers'
import { Variable${
        if (types.keySet().isEmpty()) ""
        else ", " + types.keySet().joinToString(separator = ", ") { "${it}, ${it}Variable" }
    } } from './variables'
        
class Counter {
    private id: number = -1

    getId(): number {
        this.id -= 1
        return this.id
    }
}
export const counter = new Counter()

export function createVariable(typeName: NonPrimitiveType, values: object): [Variable, DiffVariable] {
    const id: number = counter.getId()
    const variable: Variable = when(typeName, {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.entrySet().joinToString(separator = ",\n") { (typeName, typeDef) ->
            """        '${typeName}': () => new ${typeName}Variable(id, {${
                if (typeDef.asJsonObject.keySet().isEmpty()) ""
                else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                    .joinToString(separator = ",\n") { (keyName, keyDef) ->
                        "            ${keyName}: ${
                            when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                                TypeConstants.TEXT -> "String(values['${keyName}'])"
                                TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "parseInt(String(values['${keyName}']))"
                                TypeConstants.DECIMAL -> "parseFloat(String(values['${keyName}']))"
                                TypeConstants.BOOLEAN -> "Boolean(values['${keyName}']).valueOf()"
                                TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                    TypeConstants.TEXT -> "String(values['${keyName}'])"
                                    TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "parseInt(String(values['${keyName}']))"
                                    TypeConstants.DECIMAL -> "parseFloat(String(values['${keyName}']))"
                                    TypeConstants.BOOLEAN -> "Boolean(values['${keyName}']).valueOf()"
                                    else -> "String(values['${keyName}'])"
                                }
                                else -> "new ${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}(parseInt(String(values['${keyName}'])))"
                            }
                        }"
                    } + "\n        "
            }}) as Variable"""
        } + "\n    "
    }})
    return [variable, getReplaceVariableDiff(variable)]
}

export async function updateVariable(variable: Immutable<Variable>, values: object): Promise<[Variable, DiffVariable]> {
    let updatedVariable: Variable
    switch (variable.typeName) {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.entrySet().joinToString(separator = "\n") { (typeName, typeDef) ->
            """        case '${typeName}': {
            updatedVariable = new ${typeName}Variable(variable.id.hashCode(), {${
                "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                    .joinToString(separator = ",\n") { (keyName, keyDef) ->
                        """                ${keyName}: values['${keyName}'] !== undefined ? ${
                            when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                                TypeConstants.TEXT -> "String(values['${keyName}']) : variable.values.${keyName}"
                                TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "parseInt(String(values['${keyName}'])) : variable.values.${keyName}"
                                TypeConstants.DECIMAL -> "parseFloat(String(values['${keyName}'])) : variable.values.${keyName}"
                                TypeConstants.BOOLEAN -> "Boolean(values['${keyName}']).valueOf() : variable.values.${keyName}"
                                TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                    TypeConstants.TEXT -> "String(values['${keyName}']) : variable.values.${keyName}"
                                    TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "parseInt(String(values['${keyName}'])) : variable.values.${keyName}"
                                    TypeConstants.DECIMAL -> "parseFloat(String(values['${keyName}'])) : variable.values.${keyName}"
                                    TypeConstants.BOOLEAN -> "Boolean(values['${keyName}']).valueOf() : variable.values.${keyName}"
                                    else -> "String(values['${keyName}'])"
                                }
                                else -> "new ${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}(parseInt(String(values['${keyName}']))) : new ${
                                    keyDef.asJsonObject.get(
                                        KeyConstants.KEY_TYPE
                                    ).asString
                                }(variable.values.${keyName}.hashCode())"
                            }
                        }"""
                    } + "\n            "
            }})
            break
        }"""
        }
    }
        default: {
            const _exhaustiveCheck: never = variable
            return _exhaustiveCheck
        }
    }
    return [updatedVariable, mergeDiffs([getRemoveVariableDiff(variable.typeName, variable.id.hashCode()), getReplaceVariableDiff(updatedVariable)])]
}

export function deleteVariable(typeName: NonPrimitiveType, id: number): DiffVariable {
    return getRemoveVariableDiff(typeName, id)
}

export type Multiqueue = Record<string, ReadonlyArray<
    | {
        typeName: NonPrimitiveType
        op: 'create'
        values: object
    } | {
        typeName: NonPrimitiveType
        op: 'update'
        id: number
        active?: boolean
        values: object
    } | {
        typeName: NonPrimitiveType
        op: 'delete'
        id: number
    }>>

export async function executeQueue(multiqueue: Multiqueue): Promise<[Record<string, Array<any>>, DiffVariable]> {
    const diffs: Array<DiffVariable> = []
    const result: Record<string, Array<any>> = {}
    for (const queueName in multiqueue) {
        const queue = multiqueue[queueName]
        result[queueName] = []
        var symbolFlag = true
        for (const mutation of queue) {
            if (symbolFlag) {
                switch (mutation.op) {
                    case 'create': {
                        const [variable, diff] = createVariable(mutation.typeName, mutation.values)
                        result[queueName].push(variable)
                        diffs.push(diff)
                        break
                    }
                    case 'update': {
                        const variable = await getVariable(mutation.typeName, mutation.id)
                        if (variable !== undefined) {
                            const [updatedVariable, diff] = await updateVariable(variable, mutation.values)
                            result[queueName].push(updatedVariable)
                            diffs.push(diff)
                        } else {
                            result[queueName].push({})
                            symbolFlag = false
                        }
                        break
                    }
                    case 'delete': {
                        const variable = await getVariable(mutation.typeName, mutation.id)
                        if (variable !== undefined) {
                            result[queueName].push(variable)
                        } else {
                            result[queueName].push({})
                        }
                        const diff = deleteVariable(mutation.typeName, mutation.id)
                        diffs.push(diff)
                        break
                    }
                    default: {
                        const _exhaustiveCheck: never = mutation
                        return _exhaustiveCheck
                    }
                }
            }
        }
    }
    return [result, mergeDiffs(diffs)]
}
"""
}

fun getLayersTs(): String {
    return """import { HashSet, Vector } from 'prelude-ts'
import { immerable, Immutable } from 'immer'
import { NonPrimitiveType } from './types'
import { db } from './dexie'
import { Variable, VariableId${
        if (types.keySet().isEmpty()) ""
        else ", " + types.keySet().joinToString(separator = ", ") { "${it}, ${it}Variable" }
    } } from './variables'
import { DiffRow${
        if (types.keySet().isEmpty()) ""
        else ", " + types.keySet().joinToString(separator = ", ") { "${it}Row" }
    } } from './rows'

export function mergeDiffs(diffs: ReadonlyArray<DiffVariable>): DiffVariable {
    const result = diffs.reduce((acc, diff) => {
        Object.keys(diff.variables).forEach(typeName => {
            acc.variables[typeName].replace = acc.variables[typeName].replace.filter((x: Variable) => !diff.variables[typeName].remove.anyMatch(y => x.id.hashCode() === y.hashCode())).addAll(diff.variables[typeName].replace)
            acc.variables[typeName].remove = acc.variables[typeName].remove.filter((x: VariableId) => !diff.variables[typeName].replace.anyMatch(y => x.hashCode() === y.id.hashCode())).addAll(diff.variables[typeName].remove)
        })
        return acc
    }, new DiffVariable())
    return result
}

type DiffVariables = {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = ",\n") {
            """    ${it}: {
        replace: HashSet<Immutable<${it}Variable>>,
        remove: HashSet<Immutable<${it}>>
    }"""
        } + "\n"
    }}

export class DiffVariable {
    [immerable] = true
    // readonly id: number
    active: boolean
    variables: DiffVariables

    constructor(id: number = -1, active: boolean = true, variables: DiffVariables = {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = ",\n") {
            """        ${it}: {
            replace: HashSet.of(),
            remove: HashSet.of()
        }"""
        } + "\n"
    }    }) {
        // this.id = id
        this.active = active
        this.variables = variables
    }

    equals(other: DiffVariable): boolean {
        if (!other) {
            return false;
        }
        return false
        // return this.id === other.id
    }

    hashCode(): number {
        return 0
    }

    toString(): string {
        return JSON.stringify(this, null, 2)
    }

    toRow(): DiffRow {
        return new DiffRow({${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = ",\n") {
            """            ${it}: {
                replace: this.variables.${it}.replace.toArray().map(x => x.toRow()),
                remove: this.variables.${it}.remove.toArray().map(x => x.hashCode())
            }"""
        } + "\n"
    }        })
    }
}

export function getReplaceVariableDiff(variable: Immutable<Variable>): DiffVariable {
    const diff: DiffVariable = new DiffVariable()
    switch (variable.typeName) {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = "\n") {
            """        case '${it}': {
            diff.variables[variable.typeName].replace = diff.variables[variable.typeName].replace.add(variable)
            break
        }"""
        } + "\n"
    }        default: {
            const _exhaustiveCheck: never = variable
            return _exhaustiveCheck
        }
    }
    return diff
}

export function getRemoveVariableDiff(typeName: NonPrimitiveType, id: number): DiffVariable {
    const diff: DiffVariable = new DiffVariable()
    switch (typeName) {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = "\n") {
            """        case '${it}': {
            diff.variables[typeName].remove = diff.variables[typeName].remove.add(new ${it}(id))
            break
        }"""
        } + "\n"
    }        default: {
            const _exhaustiveCheck: never = typeName
            return _exhaustiveCheck
        }
    }
    return diff
}

export async function getVariable(typeName: NonPrimitiveType, id: number, overlay: Vector<DiffVariable> = Vector.of()): Promise<Variable | undefined> {
    const diffs: Array<DiffVariable> = (await db.diffs.orderBy('id').reverse().toArray()).map(x => DiffRow.toVariable(x))
    switch (typeName) {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = "\n") {
            """        case '${it}': {
            for (const diff of overlay.reverse().toArray()) {
                for (const variable of diff.variables[typeName].replace.toArray()) {
                    if (variable.id.hashCode() === id) {
                        return variable as Variable
                    }
                }
                if (diff.variables[typeName].remove.anyMatch(x => x.hashCode() === id)) {
                    return undefined
                }
            }
            for (const diff of diffs) {
                for (const variable of diff.variables[typeName].replace.toArray()) {
                    if (variable.id.hashCode() === id) {
                        return variable as Variable
                    }
                }
                if (diff.variables[typeName].remove.anyMatch(x => x.hashCode() === id)) {
                    return undefined
                }
            }
            const row = await db[typeName].get(id)
            if (row !== undefined) {
                return ${it}Row.toVariable(row) as Variable
            }
            return undefined
        }"""
        } + "\n"
    }        default: {
            const _exhaustiveCheck: never = typeName
            return _exhaustiveCheck
        }
    }
}

export async function getVariables(typeName: NonPrimitiveType, overlay: Vector<DiffVariable> = Vector.of()): Promise<Vector<Immutable<Variable>>> {
    const diffs: Array<DiffVariable> = (await db.diffs.orderBy('id').reverse().toArray()).map(x => DiffRow.toVariable(x))
    const rows = await db[typeName].orderBy('id').toArray()
    switch (typeName) {${
        if (types.keySet().isEmpty()) ""
        else "\n" + types.keySet().joinToString(separator = "\n") {
            """        case '${it}': {
            let composedVariables = Vector.of<Immutable<Variable>>().appendAll(rows ? rows.map(x => ${it}Row.toVariable(x)) : [])
            diffs?.forEach(diff => {
                composedVariables = composedVariables.filter(x => !diff.variables[typeName].remove.anyMatch(y => x.id.hashCode() === y.hashCode())).appendAll(diff.variables[typeName].replace)
            })
            return composedVariables
        }"""
        } + "\n"
    }        default: {
            const _exhaustiveCheck: never = typeName
            return _exhaustiveCheck
        }
    }
}
"""
}

fun getDexieTs(): String {
    return """import Dexie from 'dexie'
import { Immutable } from 'immer'
import { DiffRow${
        if (types.keySet().isEmpty()) ""
        else ", " + types.keySet().joinToString(separator = ", ") { "${it}Row" }
    } } from './rows'

class Database extends Dexie {
    diffs: Dexie.Table<Immutable<DiffRow>, number>
${types.keySet().joinToString(separator = "\n") { "    ${it}: Dexie.Table<Immutable<${it}Row>, number>" }}

    constructor() {
        super('Database')
        this.version(1).stores({
            diffs: '++id',
${
        types.entrySet().joinToString(separator = ",\n") { (typeName, typeDef) ->
            "            ${typeName}:'++id${
                if (typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.keySet().isEmpty()) ""
                else ", " + typeDef.asJsonObject.get("uniqueConstraints").asJsonObject.entrySet()
                    .joinToString(separator = ", ") { (_, keyNames) ->
                        "[${
                            keyNames.asJsonArray.joinToString(separator = "+") { it.asString }
                        }]"
                    }
            }'"
        }
    }
        })
        
        this.diffs = this.table('diffs')
${types.keySet().joinToString(separator = "\n") { "        this.${it} = this.table('${it}')" }}

        this.diffs.mapToClass(DiffRow)
${types.keySet().joinToString(separator = "\n") { "        this.${it}.mapToClass(${it}Row)" }}
    }
}

export const db = new Database()
"""
}

fun getFunctionsTs(): String {
    return """import { Function } from './function'

export type FunctionName =${"\n" + types.keySet().joinToString(separator = "\n") {
        """    | 'create${it}'
    | 'delete${it}' """
    }
    }

export const functions: Record<FunctionName, Function> = {${
        types.entrySet().joinToString(separator = ",\n") { (typeName, typeDef) ->
            """    create${typeName}: {
        inputs: {
${
                typeDef.asJsonObject.get("keys").asJsonObject.entrySet().filter { (_, keyDef) ->
                    keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString != TypeConstants.FORMULA
                }.joinToString(separator = ",\n") { (keyName, keyDef) ->
                    """            ${keyName}: {
                type: '${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}'
            }"""
                } + "\n"
            }        },
        outputs: {
            variable: {
                type: '${typeName}',
                op: 'create',
                values: {
${
                typeDef.asJsonObject.get("keys").asJsonObject.entrySet().filter { (_, keyDef) ->
                    keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString != TypeConstants.FORMULA
                }.joinToString(separator = ",\n") { (keyName, keyDef) ->
                    """                    ${keyName}: {
                        expectedReturnType: '${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> "Text"
                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "Number"
                            TypeConstants.DECIMAL -> "Decimal"
                            TypeConstants.BOOLEAN -> "Boolean"
                            TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "Number"
                                TypeConstants.DECIMAL -> "Decimal"
                                TypeConstants.BOOLEAN -> "Boolean"
                                else -> "Text"
                            }
                            else -> "Number"
                        }
                    }',
                        op: '.',
                        types: [],
                        args: ['${keyName}']
                    }"""
                }
            }
                }
            }
        }
    },
    delete${typeName}: {
        inputs: {
            id: {
                type: '${typeName}'
            }
        },
        outputs: {
            variable: {
                type: '${typeName}',
                op: 'delete',
                id: {
                    expectedReturnType: 'Number',
                    op: '.',
                    types: [],
                    args: ['id']
                }
            }
        }
    }"""
        }
    }
}
"""
}

fun getListTs(typeName: String, typeDef: JsonObject): String {
    return """import { Draft, Immutable } from 'immer'
import { Vector } from 'prelude-ts'
import tw from 'twin.macro'
import { useImmerReducer } from 'use-immer'
import { Container, Item, none } from '../../../main/commons'
import { Table } from '../../../main/Table'
import * as Grid from './grids/List'
import { Query, Filter, Args, getQuery, updateQuery, applyFilter } from '../../../main/Filter'
import Drawer from '@material-ui/core/Drawer'
import { useState } from 'react'
import { types } from '../../../main/types'
import { withRouter } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '../../../main/dexie'
import { DiffRow, ${typeName}Row } from '../../../main/rows'
import { ${typeName}Variable } from '../../../main/variables'

type State = Immutable<{
    typeName: '${typeName}'
    query: Query
    limit: number
    offset: number
    page: number
    columns: Vector<Array<string>>
}>

export type Action =
    | ['limit', number]
    | ['offset', number]
    | ['page', number]
    | ['query', Args]

const initialState: State = {
    typeName: '${typeName}',
    query: getQuery('${typeName}'),
    limit: 5,
    offset: 0,
    page: 1,
    columns: Vector.of(${
            typeDef.asJsonObject.get("keys").asJsonObject.keySet()
                .joinToString(separator = ", ") { "['values', '${it}']" }
        })
}

function reducer(state: Draft<State>, action: Action) {
    switch (action[0]) {
        case 'query': {
            updateQuery(state.query, action[1])
            break
        }
        case 'limit': {
            state.limit = Math.max(initialState.limit, action[1])
            return;
        }
        case 'offset': {
            state.offset = Math.max(0, action[1])
            state.page = Math.max(0, action[1]) + 1
            return;
        }
        case 'page': {
            state.page = action[1]
            return;
        }
        default: {
            const _exhaustiveCheck: never = action;
            return _exhaustiveCheck;
        }
    }
}

function Component(props) {
    const [state, dispatch] = useImmerReducer<State, Action>(reducer, initialState)
    const rows = useLiveQuery(() => db.${typeName}.orderBy('id').toArray())
    var composedVariables = Vector.of<Immutable<${typeName}Variable>>().appendAll(rows ? rows.map(x => ${typeName}Row.toVariable(x)) : [])
    const diffs = useLiveQuery(() => db.diffs.toArray())?.map(x => DiffRow.toVariable(x))
    diffs?.forEach(diff => {
        composedVariables = composedVariables.filter(x => !diff.variables[state.typeName].remove.anyMatch(y => x.id.toString() === y.toString())).filter(x => !diff.variables[state.typeName].replace.anyMatch(y => y.id.toString() === x.id.toString())).appendAll(diff.variables[state.typeName].replace)
    })
    const variables = composedVariables.filter(variable => applyFilter(state.query, variable)).reverse().toArray()
    const [open, setOpen] = useState(false)
    const type = types[state.typeName]

    const updateQuery = (args: Args) => {
        dispatch(['query', args])
    }

    const updatePage = (args: ['limit', number] | ['offset', number] | ['page', number]) => {
        dispatch([args[0], args[1]])
    }

    return (
        <Container area={none} layout={Grid.layouts.main} className='p-10'>
            <Item area={Grid.header} align='center' className='flex'>
                <Title>{type.name}s</Title>
                <button onClick={() => { props.history.push('${
            "/${
                typeName.subSequence(0, 1).toString().toLowerCase()
            }${typeName.substring(1)}".split("(?=\\p{Upper})".toRegex()).joinToString(separator = "-") {
                "${
                    it.subSequence(0, 1).toString().toLowerCase()
                }${it.substring(1)}"
            }
        }') }} className='text-3xl font-bold text-white bg-gray-800 rounded-md px-2'>+</button>
            </Item>
            <Item area={Grid.filter} justify='end' align='center'>
                <Button onClick={() => setOpen(true)}>Filter</Button>
                <Drawer open={open} onClose={() => setOpen(false)} anchor={'right'}>
                    <Filter typeName={state.typeName} query={state.query} updateQuery={updateQuery} />
                </Drawer>
            </Item>
            <Table area={Grid.table} state={state} updatePage={updatePage} variables={variables} columns={state.columns.toArray()} />
        </Container>
    )
}

export default withRouter(Component)

const Title = tw.div`text-4xl text-gray-800 font-bold mx-1 inline-block whitespace-nowrap`

const Button = tw.button`bg-gray-900 text-white text-center font-bold p-2 mx-1 uppercase w-40 h-full max-w-sm rounded-lg focus:outline-none`
"""
}

fun getMappersTs(): String {
    return """import { Immutable } from 'immer'
import { Vector } from 'prelude-ts'
import { FunctionName, functions } from "./functions"
import { NonPrimitiveType, types } from "./types"
import { Variable } from "./variables"
import { Query, getQuery, applyFilter } from './Filter'
import { executeFunction } from "./function"
import { DiffVariable, getVariables, mergeDiffs } from "./layers"

export type Mapper = {
    query: boolean
    queryParams: Array<string>
    functionName: FunctionName
    functionInput: string
}

export type MapperName = ${
    "\n" + types.entrySet()
        .filter { (_, typeDef) -> typeDef.asJsonObject.get("lists").asJsonObject.keySet().isNotEmpty() }
        .joinToString(separator = "\n\n") { (typeName, typeDef) ->
            typeDef.asJsonObject.get("lists").asJsonObject.entrySet()
                .joinToString(separator = "\n") { (refTypeName, _) ->
                    """    | 'create${refTypeName}For${typeName}'
    | 'delete${refTypeName}For${typeName}'"""
                }
        }
    }

export const mappers: Record<MapperName, Mapper> = {${
        "\n" + types.entrySet()
            .filter { (_, typeDef) -> typeDef.asJsonObject.get("lists").asJsonObject.keySet().isNotEmpty() }
            .joinToString(separator = ",\n\n") { (typeName, typeDef) ->
                typeDef.asJsonObject.get("lists").asJsonObject.entrySet()
                    .joinToString(separator = ",\n") { (refTypeName, refTypeKey) ->
                        """    create${refTypeName}For${typeName}: {
        query: false,
        queryParams: [],
        functionName: 'create${refTypeName}',
        functionInput: '${refTypeKey.asString}'
    },
    delete${refTypeName}For${typeName}: {
        query: true,
        queryParams: ['${refTypeKey.asString}'],
        functionName: 'delete${refTypeName}',
        functionInput: 'id'
    }"""
                    }
            } + "\n"
    }}

type MapperArgs = {
    queryParams: object
    args: Array<object>
}

export function isNonPrimitive(typeName: string): typeName is NonPrimitiveType {
    return Object.keys(types).includes(typeName)
}

// Note. Not only a function, mapper should also be based on a circuit.
// This will allow recursive creation and deletion of hierarchies.
export async function executeMapper(mapper: Mapper, args: MapperArgs, overlay: Vector<DiffVariable>): Promise<[Array<object>, boolean, DiffVariable]> {
    console.log('mapper', args)
    const fx = functions[mapper.functionName]
    const fi = fx.inputs[mapper.functionInput]
    var result: Array<object> = []
    var diffs = Vector.of<DiffVariable>()
    if (isNonPrimitive(fi.type)) {
        if (mapper.query) {
            const query: Query = getQuery(fi.type)
            for (const queryParam in args.queryParams) {
                if (mapper.queryParams.includes(queryParam) && Object.keys(query.values).includes(queryParam)) {
                    const value = query.values[queryParam]
                    value.checked = true
                    if ('operator' in value) {
                        switch (value.type) {
                            case 'Text': {
                                value.value = String(args.queryParams[queryParam])
                                break
                            }
                            case 'Number':
                            case 'Date':
                            case 'Timestamp':
                            case 'Time': {
                                value.value = parseInt(String(args.queryParams[queryParam]))
                                break
                            }
                            case 'Decimal': {
                                value.value = parseFloat(String(args.queryParams[queryParam]))
                                break
                            }
                            case 'Boolean': {
                                value.value = Boolean(String(args.queryParams[queryParam])).valueOf()
                                break
                            }
                        }
                    } else {
                        value.value.id.value = String(args.queryParams[queryParam])
                        value.value.id.checked = true
                    }
                    query[queryParam] = value
                }
            }
            const unfilteredVariables = await getVariables(fi.type, overlay)
            const variables: Array<Immutable<Variable>> = unfilteredVariables.filter(variable => applyFilter(query, variable)).toArray()
            for (let index = 0; index < variables.length; index++) {
                const variable = variables[index]
                if (index < args.args.length) {
                    const functionArgs = args.args[index]
                    functionArgs[mapper.functionInput] = variable.id.toString()
                    const [functionResult, symbolFlag, diff] = await executeFunction(fx, functionArgs, overlay)
                    if (!symbolFlag) {
                        return [result, false, mergeDiffs(diffs.toArray())]
                    }
                    result.push(functionResult)
                    diffs = diffs.append(diff)
                } else {
                    const functionArgs = args.args[args.args.length - 1]
                    functionArgs[mapper.functionInput] = variable.id.toString()
                    const [functionResult, symbolFlag, diff] = await executeFunction(fx, functionArgs, overlay)
                    if (!symbolFlag) {
                        return [result, false, mergeDiffs(diffs.toArray())]
                    }
                    result.push(functionResult)
                    diffs = diffs.append(diff)
                }
            }
        } else {
            for (const key in args.args) {
                const arg = args.args[key]
                const [functionResult, symbolFlag, diff] = await executeFunction(fx, arg, overlay)
                if (!symbolFlag) {
                    return [result, false, mergeDiffs(diffs.toArray())]
                }
                result.push(functionResult)
                diffs = diffs.append(diff)
            }
        }
    }
    return [result, true, mergeDiffs(diffs.toArray())]
}
"""
}

fun getCircuitsTs(): String {
    return """import { Circuit } from "./circuit"

export type CircuitName = ${
    "\n" + types.keySet().joinToString(separator = "\n") {
        """    | 'create${it}'
    | 'delete${it}'"""
    } }

export const circuits: Record<CircuitName, Circuit> = {${
        "\n" + types.entrySet().joinToString(",\n") { (typeName, typeDef) ->
            """    create${typeName}: {
        inputs: {
${
                if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
                else typeDef.asJsonObject.get("keys").asJsonObject.entrySet().filter { (_, keySet) ->
                    keySet.asJsonObject.get(KeyConstants.KEY_TYPE).asString != TypeConstants.FORMULA
                }.joinToString(separator = ",\n") { (keyName, keySet) ->
                    """            ${keyName}: {
                type: '${keySet.asJsonObject.get(KeyConstants.KEY_TYPE).asString}'
            }"""
                }
            }${
                if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
                else (if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) "" else ",\n") +
                        typeDef.asJsonObject.get("lists").asJsonObject.entrySet()
                            .joinToString(separator = ",\n") { (refTypeName, _) ->
                                """            ${
                                    refTypeName.subSequence(0, 1).toString()
                                        .toLowerCase() + refTypeName.substring(1) + "List"
                                }: {
                type: []
            }"""
                            }
            }
        },
        computations: {
            c1: {
                order: 1,
                type: 'function',
                exec: 'create${typeName}',
                connect: {${
                if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
                else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet().filter { (_, keySet) ->
                    keySet.asJsonObject.get(KeyConstants.KEY_TYPE).asString != TypeConstants.FORMULA
                }.joinToString(separator = ",\n") { (keyName, _) ->
                    """                    ${keyName}: ['input', '${keyName}']"""
                } + "\n"
            }                }
            }${
                if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
                else ",\n" + typeDef.asJsonObject.get("lists").asJsonObject.entrySet().mapIndexed { index, (refTypeName, refKeyName) ->
                    """            c${index + 2}: {
                order: ${index + 2},
                type: 'mapper',
                exec: 'create${refTypeName}For${typeName}',
                connect: {
                    queryParams: {},
                    args: ['input', '${
                        refTypeName.subSequence(0, 1).toString()
                            .toLowerCase() + refTypeName.substring(1) + "List"
                    }'],
                    overrides: {
                        ${refKeyName}: ['computation', 'c1', 'variable']
                    }
                }
            }""" }.joinToString(separator = ",\n")
            }
        },
        outputs: {
            ${ typeName.subSequence(0, 1).toString().toLowerCase() + typeName.substring(1) }: ['c1', 'variable']${
                if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
                else ",\n" + typeDef.asJsonObject.get("lists").asJsonObject.entrySet().mapIndexed { index, (refTypeName, _) ->
                    """            ${ refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List" }: ['c${index+2}', '']""" 
                }.joinToString(separator = ",\n")
            }
        }
    },
    delete${typeName}: {
        inputs: {
            id: {
                type: '${typeName}'
            },
            items: {
                type: []
            }
        },
        computations: {${
                if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
                else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.entrySet().mapIndexed { index, (refTypeName, refKeyName) ->
                    """            c${index + 1}: {
                order: ${index + 1},
                type: 'mapper',
                exec: 'delete${refTypeName}For${typeName}',
                connect: {
                    queryParams: {
                        ${refKeyName}: ['input', 'id']
                    },
                    args: ['input', 'items'],
                    overrides: {}
                }
            }""" }.joinToString(separator = ",\n")
            }${
                if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
                else ",\n" + """            c${typeDef.asJsonObject.get("lists").asJsonObject.keySet().size + 1}: {
                order: ${typeDef.asJsonObject.get("lists").asJsonObject.keySet().size + 1},
                type: 'function',
                exec: 'delete${typeName}',
                connect: {
                    id: ['input', 'id']
                }
            }"""
            }
        },
        outputs: {
            ${ typeName.subSequence(0, 1).toString().toLowerCase() + typeName.substring(1) }: ['c${typeDef.asJsonObject.get("lists").asJsonObject.keySet().size + 1}', 'variable']
        }
    }""" }}
}
"""
}

fun getShowTs(typeName: String, typeDef: JsonObject): String {
    return """import React, { useEffect, useState } from 'react'
import { Immutable, Draft } from 'immer'
import { useImmerReducer } from 'use-immer'
import tw from 'twin.macro'
import { HashSet, Vector } from 'prelude-ts'
import { Drawer } from '@material-ui/core'
import { executeCircuit } from '../../../main/circuit'
import { types } from '../../../main/types'
import { Container, Item, none } from '../../../main/commons'
import { Table } from '../../../main/Table'
import { Query, Filter, Args, getQuery, updateQuery, applyFilter } from '../../../main/Filter'
import * as Grid from './grids/Show'
import * as Grid2 from './grids/List'
import { withRouter, Link } from 'react-router-dom'
import { circuits } from '../../../main/circuits'
import { iff, when } from '../../../main/utils'
import { db } from '../../../main/dexie'
import { useCallback } from 'react'
import { updateVariable } from '../../../main/mutation'
import { useLiveQuery } from 'dexie-react-hooks'
import { DiffRow, ${
            setOf<String>(
                typeName,
                *(typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                    .map { (_, keyDef) -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString }.toTypedArray()),
                *(typeDef.asJsonObject.get("lists").asJsonObject.keySet().toTypedArray()),
                *(typeDef.asJsonObject.get("lists").asJsonObject.keySet().flatMap {
                    types.get(it).asJsonObject.get("keys").asJsonObject.entrySet()
                        .map { (_, keyDef) -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString }
                }.toTypedArray())
            )
                .filter { !primitiveTypes.contains(it) && it != TypeConstants.FORMULA }
                .sorted().joinToString(separator = ", ") { "${it}Row" }
        } } from '../../../main/rows'
import { ${
            setOf<String>(
                typeName,
                *(typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                    .map { (_, keyDef) -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString }.toTypedArray()),
                *(typeDef.asJsonObject.get("lists").asJsonObject.keySet().toTypedArray()),
                *(typeDef.asJsonObject.get("lists").asJsonObject.keySet().flatMap {
                    types.get(it).asJsonObject.get("keys").asJsonObject.entrySet()
                        .map { (_, keyDef) -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString }
                }.toTypedArray())
            )
                .filter { !primitiveTypes.contains(it) && it != TypeConstants.FORMULA }
                .sorted().joinToString(separator = ", ") { "${it}, ${it}Variable" }
        } } from '../../../main/variables'

type State = Immutable<{
    mode: 'create' | 'update' | 'show'
    variable: ${typeName}Variable${
            if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
            else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = "\n") {
                """    ${it.subSequence(0, 1).toString().toLowerCase() + it.substring(1) + "List"}: {
        typeName: '${it}'
        query: Query
        limit: number
        offset: number
        page: number
        columns: Vector<Array<string>>
        variable: ${it}Variable
        variables: HashSet<Immutable<${it}Variable>>
    }"""
            }
        }
}>

export type Action =
    | ['toggleMode']
    | ['resetVariable', State]
 ${
            if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
            else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = "\n") { (keyName, keyDef) ->
                    "    | ['variable', '${keyName}', ${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> "string"
                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                            TypeConstants.DECIMAL -> "number"
                            TypeConstants.BOOLEAN -> "boolean"
                            TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                                TypeConstants.DECIMAL -> "number"
                                TypeConstants.BOOLEAN -> "boolean"
                                else -> "string"
                            }
                            else -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString
                        }
                    }]"
                }
 }
${
            if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
            else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet()
                .joinToString(separator = "\n\n") { refTypeName ->
                    """    | ['${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }', 'limit', number]
    | ['${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }', 'offset', number]
    | ['${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}', 'page', number]
    | ['${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}', 'query', Args]${
                        if (types.get(refTypeName).asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
                        else "\n" + types.get(refTypeName).asJsonObject.get("keys").asJsonObject.entrySet()
                            .joinToString(separator = "\n") { (keyName, keyDef) ->
                                "    | ['${
                                    refTypeName.subSequence(0, 1).toString()
                                        .toLowerCase() + refTypeName.substring(1) + "List"
                                }', 'variable', '${keyName}', ${
                                    when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                                        TypeConstants.TEXT -> "string"
                                        TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                                        TypeConstants.DECIMAL -> "number"
                                        TypeConstants.BOOLEAN -> "boolean"
                                        TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "number"
                                            TypeConstants.DECIMAL -> "number"
                                            TypeConstants.BOOLEAN -> "boolean"
                                            else -> "string"
                                        }
                                        else -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString
                                    }
                                }]"
                            }
                    }
    | ['${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }', 'addVariable']"""
                }
        }
    | ['replace', 'variable', ${typeName}Variable]${
            if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
            else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = "\n") {
                "    | ['replace', '${
                    it.subSequence(0, 1).toString().toLowerCase() + it.substring(1) + "List"
                }', HashSet<${it}Variable>]"
            }
        }

function Component(props) {

    const initialState: State = {
        mode: props.match.params[0] ? 'show' : 'create',
        variable: new ${typeName}Variable(-1, { ${
            typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = ", ") { (keyName, keyDef) ->
                    "${keyName}: ${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> "''"
                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "0"
                            TypeConstants.DECIMAL -> "0"
                            TypeConstants.BOOLEAN -> "false"
                            TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "0"
                                TypeConstants.DECIMAL -> "0"
                                TypeConstants.BOOLEAN -> "false"
                                else -> "''"
                            }
                            else -> "new ${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}(-1)"
                        }
                    }"
                }
        } })${
            if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
            else ",\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet()
                .joinToString(separator = ",\n") { refTypeName ->
                    """        ${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }: {
            typeName: '${refTypeName}',
            query: getQuery('${refTypeName}'),
            limit: 5,
            offset: 0,
            page: 1,
            columns: Vector.of(${
                        types.get(refTypeName).asJsonObject.get("keys").asJsonObject.entrySet()
                            .joinToString(separator = ", ") { (keyName, _) -> "['values', '${keyName}']" }
                    }),
            variable: new ${refTypeName}Variable(-1, { ${
                        types.get(refTypeName).asJsonObject.get("keys").asJsonObject.entrySet()
                            .joinToString(separator = ", ") { (keyName, keyDef) ->
                                "${keyName}: ${
                                    when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                                        TypeConstants.TEXT -> "''"
                                        TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "0"
                                        TypeConstants.DECIMAL -> "0"
                                        TypeConstants.BOOLEAN -> "false"
                                        TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "0"
                                            TypeConstants.DECIMAL -> "0"
                                            TypeConstants.BOOLEAN -> "false"
                                            else -> "''"
                                        }
                                        else -> "new ${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}(-1)"
                                    }
                                }"
                            }
                    } }),
            variables: HashSet.of<${refTypeName}Variable>()
        }"""
                }
        }
    }
    
    function reducer(state: Draft<State>, action: Action) {
        switch (action[0]) {
            case 'toggleMode': {
                state.mode = when(state.mode, {
                    'create': 'create',
                    'update': 'show',
                    'show': 'update'
                })
                break
            }
            case 'resetVariable': {
                return action[1]
            }
            ${
                if(typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
                else """case 'variable': {
                switch (action[1]) {${
                    if(typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
                    else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.keySet().joinToString(separator = "\n") {
                        """                    case '${it}': {
                        state[action[0]][action[1]] = action[2]
                        break
                    }"""
                    }
                }
                    default: {
                        const _exhaustiveCheck: never = action;
                        return _exhaustiveCheck;
                    }
                }
                break
            }"""
            }
            ${
            if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) "" 
            else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet()
                .joinToString(separator = "\n") { refTypeName ->
                    """            case '${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }': {
                switch (action[1]) {
                    case 'limit': {
                        state[action[0]].limit = Math.max(initialState.${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }.limit, action[2])
                        break
                    }
                    case 'offset': {
                        state[action[0]].offset = Math.max(0, action[2])
                        state[action[0]].page = Math.max(0, action[2]) + 1
                        break
                    }
                    case 'page': {
                        state[action[0]].page = action[2]
                        break
                    }
                    case 'query': {
                        updateQuery(state[action[0]].query, action[2])
                        break
                    }
                    case 'variable': {
                        switch (action[2]) {${
                        if (types.get(refTypeName).asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
                        else "\n" + types.get(refTypeName).asJsonObject.get("keys").asJsonObject.keySet()
                            .joinToString(separator = "\n") {
                                """                            case '${it}': {
                                state[action[0]][action[1]]['values'][action[2]] = action[3]
                                break
                            }"""
                            }
                        }
                            default: {
                                const _exhaustiveCheck: never = action;
                                return _exhaustiveCheck;
                            }
                        }
                        break
                    }
                    case 'addVariable': {
                        state.${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }.variables = state.${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }.variables.add(new ${refTypeName}Variable(-1, {${
                        if (types.get(refTypeName).asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
                        else types.get(refTypeName).asJsonObject.get("keys").asJsonObject.entrySet()
                            .joinToString(separator = ", ") { (keyName, keyDef) ->
                                "${keyName}: ${
                                    when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                                        TypeConstants.TEXT -> "state.${
                                            refTypeName.subSequence(0, 1).toString()
                                                .toLowerCase() + refTypeName.substring(1) + "List"
                                        }.variable.values.${keyName}"
                                        TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "state.${
                                            refTypeName.subSequence(
                                                0,
                                                1
                                            ).toString().toLowerCase() + refTypeName.substring(1) + "List"
                                        }.variable.values.${keyName}"
                                        TypeConstants.DECIMAL -> "state.${
                                            refTypeName.subSequence(0, 1).toString()
                                                .toLowerCase() + refTypeName.substring(1) + "List"
                                        }.variable.values.${keyName}"
                                        TypeConstants.BOOLEAN -> "state.${
                                            refTypeName.subSequence(0, 1).toString()
                                                .toLowerCase() + refTypeName.substring(1) + "List"
                                        }.variable.values.${keyName}"
                                        TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "state.${
                                                refTypeName.subSequence(
                                                    0,
                                                    1
                                                ).toString().toLowerCase() + refTypeName.substring(1) + "List"
                                            }.variable.values.${keyName}"
                                            TypeConstants.DECIMAL -> "state.${
                                                refTypeName.subSequence(0, 1).toString()
                                                    .toLowerCase() + refTypeName.substring(1) + "List"
                                            }.variable.values.${keyName}"
                                            TypeConstants.BOOLEAN -> "state.${
                                                refTypeName.subSequence(0, 1).toString()
                                                    .toLowerCase() + refTypeName.substring(1) + "List"
                                            }.variable.values.${keyName}"
                                            else -> "state.${
                                                refTypeName.subSequence(0, 1).toString()
                                                    .toLowerCase() + refTypeName.substring(1) + "List"
                                            }.variable.values.${keyName}"
                                        }
                                        else -> "new ${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}(state.${
                                            refTypeName.subSequence(
                                                0,
                                                1
                                            ).toString().toLowerCase() + refTypeName.substring(1) + "List"
                                        }.variable.values.${keyName}.hashCode())"
                                    }
                                }"
                            }
                    }}))
                        state.${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }.variable = initialState.${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }.variable
                        break
                    }
                    default: {
                        const _exhaustiveCheck: never = action
                        return _exhaustiveCheck
                    }
                }
                break
            }"""
                }
        }
            case 'replace': {
                switch (action[1]) {
                    case 'variable': {
                        state.variable = action[2]
                        break
                    }${
                        if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
                        else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet()
                            .joinToString(separator = "\n") { refTypeName ->
                                """                    case '${
                                    refTypeName.subSequence(0, 1).toString()
                                        .toLowerCase() + refTypeName.substring(1) + "List"
                                }': {
                        state.${
                                    refTypeName.subSequence(0, 1).toString()
                                        .toLowerCase() + refTypeName.substring(1) + "List"
                                }.variables = action[2]
                        break
                    }"""
                            }
        }
                    default: {
                        const _exhaustiveCheck: never = action
                        return _exhaustiveCheck
                    }
                }
                break
            }
            default: {
                const _exhaustiveCheck: never = action
                return _exhaustiveCheck
            }
        }
    }

    const [state, dispatch] = useImmerReducer<State, Action>(reducer, initialState)

    const ${typeName.subSequence(0, 1).toString().toLowerCase() + typeName.substring(1) + "Type"} = types['${typeName}']${
        if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
        else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet().filter { it != typeName }
            .joinToString(separator = "\n") { refTypeName ->
                "    const ${
                    refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Type"
                } = types['${refTypeName}']"
            }
        }
    ${
        if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
        else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet()
            .joinToString(separator = "\n\n") { refTypeName ->
                """    const [add${refTypeName}Drawer, toggleAdd${refTypeName}Drawer] = useState(false)
    const [${
                    refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1)
                }Filter, toggle${refTypeName}Filter] = useState(false)"""
            }
    }
    
    const setVariable = useCallback(async () => {
        if (props.match.params[0]) {
            const rows = await db.${typeName}.toArray()
            var composedVariables = HashSet.of<Immutable<${typeName}Variable>>().addAll(rows ? rows.map(x => ${typeName}Row.toVariable(x)) : [])
            const diffs = (await db.diffs.toArray())?.map(x => DiffRow.toVariable(x))
            diffs?.forEach(diff => {
                composedVariables = composedVariables.filter(x => !diff.variables[state.variable.typeName].remove.anyMatch(y => x.id.hashCode() === y.hashCode())).filter(x => !diff.variables[state.variable.typeName].replace.anyMatch(y => y.id.hashCode() === x.id.hashCode())).addAll(diff.variables[state.variable.typeName].replace)
            })
            const variables = composedVariables.filter(variable => variable.id.hashCode() === props.match.params[0])
            if (variables.length() === 1) {
                const variable = variables.toArray()[0]
                dispatch(['replace', 'variable', variable as ${typeName}Variable])${
                    if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
                    else "\n\n" + typeDef.asJsonObject.get("lists").asJsonObject.entrySet()
                        .joinToString(separator = "\n\n") { (refTypeName, refKeyName) ->
                            """                const ${
                                refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1)
                            }Rows = await db.${refTypeName}.toArray()
                var composed${refTypeName}Variables = HashSet.of<Immutable<${refTypeName}Variable>>().addAll(${
                                refTypeName.subSequence(
                                    0,
                                    1
                                ).toString().toLowerCase() + refTypeName.substring(1)
                            }Rows ? ${
                                refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1)
                            }Rows.map(x => ${refTypeName}Row.toVariable(x)) : [])
                diffs?.forEach(diff => {
                    composed${refTypeName}Variables = composed${refTypeName}Variables.filter(x => !diff.variables[state.${
                                refTypeName.subSequence(
                                    0,
                                    1
                                ).toString().toLowerCase() + refTypeName.substring(1) + "List"
                            }.variable.typeName].remove.anyMatch(y => x.id.hashCode() === y.hashCode())).filter(x => !diff.variables[state.${
                                refTypeName.subSequence(
                                    0,
                                    1
                                ).toString().toLowerCase() + refTypeName.substring(1) + "List"
                            }.variable.typeName].replace.anyMatch(y => y.id.hashCode() === x.id.hashCode())).addAll(diff.variables[state.${
                                refTypeName.subSequence(
                                    0,
                                    1
                                ).toString().toLowerCase() + refTypeName.substring(1) + "List"
                            }.variable.typeName].replace)
                })
                dispatch(['replace', '${
                                refTypeName.subSequence(0, 1).toString()
                                    .toLowerCase() + refTypeName.substring(1) + "List"
                            }', composed${refTypeName}Variables.filter(variable => variable.values.${refKeyName.asString}.hashCode() === props.match.params[0]) as HashSet<${refTypeName}Variable>])"""
                        }
        }
            }
        }
    }, [state.variable.typeName${
        if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
        else ", " + typeDef.asJsonObject.get("lists").asJsonObject.keySet()
            .joinToString(separator = ", ") { refTypeName ->
                "state.${
                    refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                }.variable.typeName"
            }
        }, props.match.params, dispatch])

    useEffect(() => { setVariable() }, [setVariable])

${
    setOf<String>(typeName,
        *(typeDef.asJsonObject.get("keys").asJsonObject.entrySet().map { (_, keyDef) -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString }.toTypedArray()),
        *(typeDef.asJsonObject.get("lists").asJsonObject.keySet().toTypedArray()),
        *(typeDef.asJsonObject.get("lists").asJsonObject.keySet().flatMap { 
            types.get(it).asJsonObject.get("keys").asJsonObject.entrySet().map { (_, keyDef) -> keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString } }.toTypedArray())
        )
        .filter { !primitiveTypes.contains(it) && it != TypeConstants.FORMULA }
        .sorted().joinToString(separator = "\n\n") { """    const ${it.subSequence(0, 1).toString().toLowerCase() + it.substring(1)}Rows = useLiveQuery(() => db.${it}.toArray())?.map(x => ${it}Row.toVariable(x))
    var ${it.subSequence(0, 1).toString().toLowerCase() + it.substring(1) + "List"} = HashSet.of<Immutable<${it}Variable>>().addAll(${it.subSequence(0, 1).toString().toLowerCase() + it.substring(1)}Rows ? ${it.subSequence(0, 1).toString().toLowerCase() + it.substring(1)}Rows : [])
    useLiveQuery(() => db.diffs.toArray())?.map(x => DiffRow.toVariable(x))?.forEach(diff => {
        ${it.subSequence(0, 1).toString().toLowerCase() + it.substring(1) + "List"} = ${it.subSequence(0, 1).toString().toLowerCase() + it.substring(1) + "List"}.filter(x => !diff.variables.${it}.remove.anyMatch(y => x.id.hashCode() === y.hashCode())).filter(x => !diff.variables.${it}.replace.anyMatch(y => y.id.hashCode() === x.id.hashCode())).addAll(diff.variables.${it}.replace)
    })""" }
}

${
    if (typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
    else """    const onVariableInputChange = async (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        switch (event.target.name) {
${
        typeDef.asJsonObject.get("keys").asJsonObject.entrySet().joinToString(separator = "\n") { (keyName, keyDef) ->
            """            case '${keyName}': {
                dispatch(['variable', event.target.name, ${
                when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                    TypeConstants.TEXT -> "String(event.target.value)"
                    TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "parseInt(String(event.target.value))"
                    TypeConstants.DECIMAL -> "parseFloat(String(event.target.value))"
                    TypeConstants.BOOLEAN -> "Boolean(event.target.value).valueOf()"
                    TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                        TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "parseInt(String(event.target.value))"
                        TypeConstants.DECIMAL -> "parseFloat(String(event.target.value))"
                        TypeConstants.BOOLEAN -> "Boolean(event.target.value).valueOf()"
                        else -> "String(event.target.value)"
                    }
                    else -> "new ${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}(parseInt(String(event.target.value)))"
                }
            }])
                break
            }"""
        }
    }
        }
    }"""
}${
    if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
    else "\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet()
        .joinToString(separator = "\n\n") { refTypeName ->
            """    const on${refTypeName}InputChange = async (event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        switch (event.target.name) {
${
                types.get(refTypeName).asJsonObject.get("keys").asJsonObject.entrySet()
                    .joinToString(separator = "\n") { (keyName, keyDef) ->
                        """            case '${keyName}': {
                dispatch(['${
                            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                        }', 'variable', event.target.name, ${
                            when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                                TypeConstants.TEXT -> "String(event.target.value)"
                                TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "parseInt(String(event.target.value))"
                                TypeConstants.DECIMAL -> "parseFloat(String(event.target.value))"
                                TypeConstants.BOOLEAN -> "Boolean(event.target.value).valueOf()"
                                TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                    TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> "parseInt(String(event.target.value))"
                                    TypeConstants.DECIMAL -> "parseFloat(String(event.target.value))"
                                    TypeConstants.BOOLEAN -> "Boolean(event.target.value).valueOf()"
                                    else -> "String(event.target.value)"
                                }
                                else -> "new ${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString}(parseInt(String(event.target.value)))"
                            }
                        }])
                break
            }"""
                    }
            }
        }
    }"""
        }
        }

${
    if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
    else """    const updateItemsQuery = (list: ${
        typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = " | ") { refTypeName ->
            "'${
                refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
            }'"
        }
    }) => {
        const fx = (args: Args) => {
            switch (list) {
${
        typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = "\n") { refTypeName ->
            """                case '${
                refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
            }': {
                    dispatch([list, 'query', args])
                    break
                }"""
        }
    }
                default: {
                    const _exhaustiveCheck: never = list
                    return _exhaustiveCheck
                }
            }
        }
        return fx
    }

    const updatePage = (list: ${
        typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = " | ") { refTypeName ->
            "'${
                refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
            }'"
        }
    }) => {
        const fx = (args: ['limit', number] | ['offset', number] | ['page', number]) => {
            dispatch([list, args[0], args[1]])
        }
        return fx
    }"""
}

    const createVariable = async () => {
        const [result, symbolFlag, diff] = await executeCircuit(circuits.create${typeName}, {${
            if(typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
            else "\n" + typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = ",\n") { (keyName, keyDef) ->
                    "            ${keyName}: state.variable.values.${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> keyName
                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> keyName
                            TypeConstants.DECIMAL -> keyName
                            TypeConstants.BOOLEAN -> keyName
                            TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> keyName
                                TypeConstants.DECIMAL -> keyName
                                TypeConstants.BOOLEAN -> keyName
                                else -> keyName
                            }
                            else -> "${keyName}.hashCode()"
                        }
                    }"
                }
        }${
            if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
            else (if(typeDef.asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) "" else ",\n") + typeDef.asJsonObject.get("lists").asJsonObject.keySet()
                .joinToString(separator = ",\n") { refTypeName ->
                    """            ${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }: state.${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
                    }.variables.toArray().map(variable => {
                return {${
                        if (types.get(refTypeName).asJsonObject.get("keys").asJsonObject.keySet().isEmpty()) ""
                        else "\n" + types.get(refTypeName).asJsonObject.get("keys").asJsonObject.entrySet()
                            .joinToString(",\n") { (keyName, keyDef) ->
                                "                    ${keyName}: variable.values.${
                                    when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                                        TypeConstants.TEXT -> keyName
                                        TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> keyName
                                        TypeConstants.DECIMAL -> keyName
                                        TypeConstants.BOOLEAN -> keyName
                                        TypeConstants.FORMULA -> when (keyDef.asJsonObject.get(KeyConstants.FORMULA_RETURN_TYPE).asString) {
                                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> keyName
                                            TypeConstants.DECIMAL -> keyName
                                            TypeConstants.BOOLEAN -> keyName
                                            else -> keyName
                                        }
                                        else -> "${keyName}.hashCode()"
                                    }
                                }"
                            }
                    }
                }
            })"""
                }
        }
        })
        console.log(result, symbolFlag, diff)
        if (symbolFlag) {
            db.diffs.put(diff.toRow())
        }
    }

    const modifyVariable = async () => {
        const [, diff] = await updateVariable(state.variable, state.variable.toRow().values)
        console.log(diff)
        db.diffs.put(diff.toRow())
    }

    const deleteVariable = async () => {
        const [result, symbolFlag, diff] = await executeCircuit(circuits.delete${typeName}, {
            id: state.variable.id.hashCode(),
            items: [{}]
        })
        console.log(result, symbolFlag, diff)
        if (symbolFlag) {
            db.diffs.put(diff.toRow())
        }
    }

    return iff(true,
        () => {
            return <Container area={none} layout={Grid.layouts.main}>
                <Item area={Grid.header}>
                    <Title>{when(state.mode, {
                        'create': `Create ${typeName.split("(?=\\p{Upper})".toRegex()).joinToString(separator = " ").trim()}`,
                        'update': `Update ${typeName.split("(?=\\p{Upper})".toRegex()).joinToString(separator = " ").trim()}`,
                        'show': `${typeName.split("(?=\\p{Upper})".toRegex()).joinToString(separator = " ").trim()}`
                    })}</Title>
                </Item>
                <Item area={Grid.button} justify='end' align='center' className='flex'>
                    {
                        iff(state.mode === 'create',
                            <Button onClick={async () => {
                                await createVariable()
                                props.history.push('/${
            "${
                typeName.subSequence(0, 1).toString().toLowerCase()
            }${typeName.substring(1)}".split("(?=\\p{Upper})".toRegex()).joinToString(separator = "-") {
                "${
                    it.subSequence(0, 1).toString().toLowerCase()
                }${it.substring(1)}"
            }
        }-list')
                            }}>Save</Button>,
                            iff(state.mode === 'update',
                                <>
                                    <Button onClick={() => {
                                        dispatch(['toggleMode'])
                                        setVariable()
                                    }}>Cancel</Button>
                                    <Button onClick={async () => {
                                        await modifyVariable()
                                        props.history.push('/${
            "${
                typeName.subSequence(0, 1).toString().toLowerCase()
            }${typeName.substring(1)}".split("(?=\\p{Upper})".toRegex()).joinToString(separator = "-") {
                "${
                    it.subSequence(0, 1).toString().toLowerCase()
                }${it.substring(1)}"
            }
        }-list')
                                    }}>Update</Button>
                                </>,
                                <>
                                    <Button onClick={async () => {
                                        await deleteVariable()
                                        props.history.push('/${
            "${
                typeName.subSequence(0, 1).toString().toLowerCase()
            }${typeName.substring(1)}".split("(?=\\p{Upper})".toRegex()).joinToString(separator = "-") {
                "${
                    it.subSequence(0, 1).toString().toLowerCase()
                }${it.substring(1)}"
            }
        }-list')
                                    }}>Delete</Button>
                                    <Button onClick={async () => dispatch(['toggleMode'])}>Edit</Button>
                                </>))
                    }
                </Item>
                <Container area={Grid.details} layout={Grid.layouts.details}>
${
            typeDef.asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = "\n") { (keyName, keyDef) ->
                    """                    <Item>
                        <Label>{${
                        typeName.subSequence(0, 1).toString().toLowerCase() + typeName.substring(1) + "Type"
                    }.keys.${keyName}}</Label>
                        {
                            iff(state.mode === 'create' || state.mode === 'update',
                                ${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> """<Input type='text' onChange={onVariableInputChange} value={state.variable.values.${keyName}} name='${keyName}' />,
                                <div className='font-bold text-xl'>{state.variable.values.${keyName}}</div>"""
                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> """<Input type='number' onChange={onVariableInputChange} value={state.variable.values.${keyName}} name='${keyName}' />,
                                <div className='font-bold text-xl'>{state.variable.values.${keyName}}</div>"""
                            TypeConstants.DECIMAL -> """<Input type='number' onChange={onVariableInputChange} value={state.variable.values.${keyName}} name='${keyName}' />,
                                <div className='font-bold text-xl'>{state.variable.values.${keyName}}</div>"""
                            TypeConstants.BOOLEAN -> """<Input type='text' onChange={onVariableInputChange} value={state.variable.values.${keyName}} name='${keyName}' />,
                                <div className='font-bold text-xl'>{state.variable.values.${keyName}}</div>"""
                            TypeConstants.FORMULA -> """<div className='font-bold text-xl'>{state.variable.values.${keyName}}</div>,
                                <div className='font-bold text-xl'>{state.variable.values.${keyName}}</div>"""
                            else -> """<Select onChange={onVariableInputChange} value={state.variable.values.${keyName}.hashCode()} name='${keyName}'>
                                    <option value='' selected disabled hidden>Select ${keyDef.asJsonObject.get("name").asString}</option>
                                    {${
                                keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(0, 1).toString()
                                    .toLowerCase() + keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1) + "List"
                            }.toArray().map(x => <option value={x.id.hashCode()}>{x.id.hashCode()}</option>)}
                                </Select>,
                                <div className='font-bold text-xl'>{
                                    iff(${
                                keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(0, 1).toString()
                                    .toLowerCase() + keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1) + "List"
                            }.filter(x => x.id.hashCode() === state.variable.values.${keyName}.hashCode()).length() !== 0,
                                        () => {
                                            const referencedVariable = ${
                                keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(
                                    0,
                                    1
                                ).toString()
                                    .toLowerCase() + keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1) + "List"
                            }.filter(x => x.id.hashCode() === state.variable.values.${keyName}.hashCode()).toArray()[0] as ${
                                keyDef.asJsonObject.get(
                                    KeyConstants.KEY_TYPE
                                ).asString
                            }Variable
                                            return <Link to={`/${
                                "${
                                    keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(0, 1).toString()
                                        .toLowerCase()
                                }${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1)}".split("(?=\\p{Upper})".toRegex())
                                    .joinToString(separator = "-") {
                                        "${
                                            it.subSequence(0, 1).toString().toLowerCase()
                                        }${it.substring(1)}"
                                    }
                            }/${'$'}{referencedVariable.id.hashCode()}`}>{referencedVariable.id.hashCode()}</Link>
                                        }, <Link to={`/${
                                "${
                                    keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(0, 1).toString()
                                        .toLowerCase()
                                }${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1)}".split("(?=\\p{Upper})".toRegex())
                                    .joinToString(separator = "-") {
                                        "${
                                            it.subSequence(0, 1).toString().toLowerCase()
                                        }${it.substring(1)}"
                                    }
                            }/${'$'}{state.variable.values.${keyName}.hashCode()}`}>{state.variable.values.${keyName}.hashCode()}</Link>)
                                }</div>"""
                        }
                    }
                            )
                        }
                    </Item>"""
                }
        }
                </Container>
${
    if(typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
    else typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = "\n\n") { refTypeName ->
        """                <Container area={Grid.${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"
        }} layout={Grid2.layouts.main}>
                    <Item area={Grid2.header} className='flex items-center'>
                        <Title>${
            refTypeName.split("(?=\\p{Upper})".toRegex()).joinToString(separator = " ")
        } List</Title>
                        {
                            iff(state.mode === 'create' || state.mode === 'update',
                                <button onClick={() => toggleAdd${refTypeName}Drawer(true)} className='text-3xl font-bold text-white bg-gray-800 rounded-md px-2 h-10 focus:outline-none'>+</button>,
                                undefined
                            )
                        }
                    </Item>
                    <Item area={Grid2.filter} justify='end' align='center' className='flex'>
                        <Button onClick={() => toggle${refTypeName}Filter(true)}>Filter</Button>
                        <Drawer open={${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1)
        }Filter} onClose={() => toggle${refTypeName}Filter(false)} anchor={'right'}>
                            <Filter typeName='${refTypeName}' query={state['${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}'].query} updateQuery={updateItemsQuery('${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
        }')} />
                        </Drawer>
                        <Drawer open={add${refTypeName}Drawer} onClose={() => toggleAdd${refTypeName}Drawer(false)} anchor={'right'}>
                            <div className='bg-gray-300 font-nunito h-screen overflow-y-scroll' style={{ maxWidth: '90vw' }}>
                                <div className='font-bold text-4xl text-gray-700 pt-8 px-6'>Add {${
            refTypeName.subSequence(
                0,
                1
            ).toString().toLowerCase() + refTypeName.substring(1) + "Type"
        }.name}</div>
                                <Container area={none} layout={Grid.layouts.uom} className=''>
${
            types.get(refTypeName).asJsonObject.get("keys").asJsonObject.entrySet()
                .joinToString(separator = "\n") { (keyName, keyDef) ->
                    """                                    <Item>
                                        <Label>{${
                        refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Type"
                    }.keys.${keyName}}</Label>
                                        {
                                            iff(state.mode === 'create' || state.mode === 'update',
                                                ${
                        when (keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString) {
                            TypeConstants.TEXT -> """<Input type='text' onChange={on${refTypeName}InputChange} value={state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}} name='${keyName}' />,
                                                <div className='font-bold text-xl'>{state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}}</div>"""
                            TypeConstants.NUMBER, TypeConstants.DATE, TypeConstants.TIMESTAMP, TypeConstants.TIME -> """<Input type='number' onChange={on${refTypeName}InputChange} value={state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}} name='${keyName}' />,
                                                <div className='font-bold text-xl'>{state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}}</div>"""
                            TypeConstants.DECIMAL -> """<Input type='number' onChange={on${refTypeName}InputChange} value={state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}} name='${keyName}' />,
                                                <div className='font-bold text-xl'>{state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}}</div>"""
                            TypeConstants.BOOLEAN -> """<Input type='text' onChange={on${refTypeName}InputChange} value={state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}} name='${keyName}' />,
                                                <div className='font-bold text-xl'>{state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}}</div>"""
                            TypeConstants.FORMULA -> """<div className='font-bold text-xl'>{state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}}</div>,
                                                <div className='font-bold text-xl'>{state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}}</div>"""
                            else -> """<Select onChange={on${refTypeName}InputChange} value={state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}.hashCode()} name='${keyName}'>
                                                    <option value='' selected disabled hidden>Select ${
                                keyDef.asJsonObject.get(
                                    "name"
                                ).asString
                            }</option>
                                                    {${
                                keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(
                                    0,
                                    1
                                ).toString()
                                    .toLowerCase() + keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1) + "List"
                            }.toArray().map(x => <option value={x.id.hashCode()}>{x.id.hashCode()}</option>)}
                                                </Select>,
                                                <div className='font-bold text-xl'>{
                                                    iff(${
                                keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(
                                    0,
                                    1
                                ).toString()
                                    .toLowerCase() + keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1) + "List"
                            }.filter(x => x.id.hashCode() === state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}.hashCode()).length() !== 0,
                                                        () => {
                                                            const referencedVariable = ${
                                keyDef.asJsonObject.get(
                                    KeyConstants.KEY_TYPE
                                ).asString.subSequence(0, 1).toString()
                                    .toLowerCase() + keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1) + "List"
                            }.filter(x => x.id.hashCode() === state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}.hashCode()).toArray()[0] as ${
                                keyDef.asJsonObject.get(
                                    KeyConstants.KEY_TYPE
                                ).asString
                            }Variable
                                                            return <Link to={`/${
                                "${
                                    keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(0, 1).toString()
                                        .toLowerCase()
                                }${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1)}".split("(?=\\p{Upper})".toRegex())
                                    .joinToString(separator = "-") {
                                        "${
                                            it.subSequence(0, 1).toString().toLowerCase()
                                        }${it.substring(1)}"
                                    }
                            }/${'$'}{referencedVariable.id.hashCode()}`}>{referencedVariable.id.hashCode()}</Link>
                                                        }, <Link to={`/${
                                "${
                                    keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.subSequence(0, 1).toString()
                                        .toLowerCase()
                                }${keyDef.asJsonObject.get(KeyConstants.KEY_TYPE).asString.substring(1)}".split("(?=\\p{Upper})".toRegex())
                                    .joinToString(separator = "-") {
                                        "${
                                            it.subSequence(0, 1).toString().toLowerCase()
                                        }${it.substring(1)}"
                                    }
                            }/${'$'}{state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}.hashCode()}`}>{state.${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"}.variable.values.${keyName}.hashCode()}</Link>)
                                                }</div>"""
                        }
                    }
                                            )
                                        }
                                    </Item>"""
                }
        }
                                    <Item justify='center' align='center'>
                                        <Button onClick={() => dispatch(['${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
        }', 'addVariable'])}>Add</Button>
                                    </Item>
                                </Container>
                            </div>
                        </Drawer>
                    </Item>
                    <Table area={Grid2.table} state={state['${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
        }']} updatePage={updatePage('${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
        }')} variables={state.${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
        }.variables.filter(variable => applyFilter(state['${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
        }'].query, variable)).toArray()} columns={state['${
            refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "List"
        }'].columns.toArray()} />
                </Container > """
    }
        }
            </Container>
        }, <div>Variable not found</div>)
}

export default withRouter(Component)

const Title = tw.div`py-8 text-4xl text-gray-800 font-bold mx-1 whitespace-nowrap`

const Label = tw.label`w-1/2 whitespace-nowrap`

// const InlineLabel = tw.label`inline-block w-1/2`

const Select = tw.select`p-1.5 text-gray-500 leading-tight border border-gray-400 shadow-inner hover:border-gray-600 w-full rounded-sm`

const Input = tw.input`p-1.5 text-gray-500 leading-tight border border-gray-400 shadow-inner hover:border-gray-600 w-full rounded-sm`

const Button = tw.button`bg-gray-900 text-white text-center font-bold p-2 mx-1 uppercase w-40 h-full max-w-sm rounded-lg focus:outline-none inline-block`
"""
}

fun getListGridTs(): String {
    return """import { Vector } from 'prelude-ts'
import { GridLayout, GridArea, none, validateLayout } from '../../../../main/commons'

export const header: GridArea = new GridArea('header')
export const filter: GridArea = new GridArea('filter')
export const table: GridArea = new GridArea('table')

export const layouts: { [index: string]: GridLayout } = {
    main: validateLayout({
        margin: 0,
        rowGap: '1rem',
        columnGap: '0rem',
        layout_mobile: {
            rows: Vector.of('6rem', '1fr'),
            columns: Vector.of('1fr', '1fr'),
            areas: Vector.of(
                Vector.of(header, filter),
                Vector.of(table, table)
            )
        },
        layout_sm: {
            rows: Vector.of('6rem', '1fr'),
            columns: Vector.of('1fr', '1fr'),
            areas: Vector.of(
                Vector.of(header, filter),
                Vector.of(table, table)
            )
        },
        layout_md: {
            rows: Vector.of('6rem', '1fr '),
            columns: Vector.of('1fr', '10fr', '10fr', '1fr'),
            areas: Vector.of(
                Vector.of(header, none, none, filter),
                Vector.of(table, table, table, table) 
            )
        },
        layout_lg: {
            rows: Vector.of('6rem', '1fr '),
            columns: Vector.of('1fr', '10fr', '10fr', '1fr'),
            areas: Vector.of(
                Vector.of(header, none, none, filter),
                Vector.of(table, table, table, table) 
            )
        },
        layout_xl: {
            rows: Vector.of('6rem', '1fr '),
            columns: Vector.of('1fr', '10fr', '10fr', '1fr'),
            areas: Vector.of(
                Vector.of(header, none, none, filter),
                Vector.of(table, table, table, table) 
            )
        }
    })
}"""
}

fun getShowGridTs(typeDef: JsonObject): String {
    return """import { Vector } from 'prelude-ts'
import { GridLayout, GridArea, none, validateLayout } from '../../../../main/commons'

export const header: GridArea = new GridArea('header')
export const button: GridArea = new GridArea('button')
export const details: GridArea = new GridArea('details')
${
            if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
            else typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = "\n") { refTypeName ->
                "export const ${
                    refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"
                }: GridArea = new GridArea('${
                    refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"
                }')"
            } + "\n"
        }
export const layouts: { [index: string]: GridLayout } = {
    main: validateLayout({
        margin: 1,
        rowGap: '1rem',
        columnGap: '1rem',
        layout_mobile: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr'),
            areas: Vector.of(
                Vector.of(header),
                Vector.of(details),
                Vector.of(button)${
        if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
        else ",\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = ",\n") { refTypeName ->
            "Vector.of(${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"})"
        } + "\n"
    }
            )
        },
        layout_sm: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr', '1fr'),
            areas: Vector.of(
                Vector.of(header, button),
                Vector.of(details, details)${
        if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
        else ",\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = ",\n") { refTypeName ->
            "Vector.of(${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"})"
        } + "\n"
    }
            )
        },
        layout_md: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr', '1fr', '1fr'),
            areas: Vector.of(
                Vector.of(header, header, button),
                Vector.of(details, details, details)${
        if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
        else ",\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = ",\n") { refTypeName ->
            "Vector.of(${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"})"
        } + "\n"
    }
            )
        },
        layout_lg: {
            rows: Vector.of('auto'),
            columns: Vector.of('0.25fr', '1fr', '1fr', '1fr', '1fr', '0.30fr'),
            areas: Vector.of(
                Vector.of(none, header, header, none, button, none),
                Vector.of(none, details, details, details, details, none)${
        if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
        else ",\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = ",\n") { refTypeName ->
            "Vector.of(none, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, none)"
        } + "\n"
    }
            )
        },
        layout_xl: {
            rows: Vector.of('auto'),
            columns: Vector.of('0.25fr', '1fr', '1fr', '1fr', '1fr', '0.30fr'),
            areas: Vector.of(
                Vector.of(none, header, header, none, button, none),
                Vector.of(none, details, details, details, details, none)${
        if (typeDef.asJsonObject.get("lists").asJsonObject.keySet().isEmpty()) ""
        else ",\n" + typeDef.asJsonObject.get("lists").asJsonObject.keySet().joinToString(separator = ",\n") { refTypeName ->
            "Vector.of(none, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, ${refTypeName.subSequence(0, 1).toString().toLowerCase() + refTypeName.substring(1) + "Area"}, none)"
        } + "\n"
    }
            )
        }
    }),
    details: validateLayout({
        margin: 1,
        rowGap: '1rem',
        columnGap: '2rem',
        layout_mobile: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr'),
            areas: Vector.of()
        },
        layout_sm: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr', '1fr'),
            areas: Vector.of()
        },
        layout_md: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr', '1fr', '1fr'),
            areas: Vector.of()
        },
        layout_lg: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr', '1fr', '1fr', '1fr', '1fr', '1fr'),
            areas: Vector.of()
        },
        layout_xl: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr', '1fr', '1fr', '1fr', '1fr', '1fr'),
            areas: Vector.of()
        }
    }),
    uom: validateLayout({
        margin: 1,
        rowGap: '1rem',
        columnGap: '2rem',
        layout_mobile: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr'),
            areas: Vector.of()
        },
        layout_sm: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr'),
            areas: Vector.of()
        },
        layout_md: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr'),
            areas: Vector.of()
        },
        layout_lg: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr'),
            areas: Vector.of()
        },
        layout_xl: {
            rows: Vector.of('auto'),
            columns: Vector.of('1fr'),
            areas: Vector.of()
        }
    })
}"""
}

fun getAppTs(): String {
    return """import { BrowserRouter, Switch, Route, Redirect } from 'react-router-dom'
import Navbar from './Navbar'
import { Container, Item, none } from './commons'
import * as Grid from './grids'
import { types } from './types'
${
        types.entrySet().joinToString(separator = "\n") { (typeName, typeDef) ->
            """import Show${typeName} from '../components/${typeDef.asJsonObject.get("group").asString}/${typeName}/Show'
import List${typeName} from '../components/${typeDef.asJsonObject.get("group").asString}/${typeName}/List'"""
        }
    }

function App() {
  console.log(JSON.stringify(types, null, 4))
  return (
    <div className='App font-nunito bg-gray-100'>
      <BrowserRouter>
        <Container area={none} layout={Grid.layouts.main} className='h-screen'>
          <Item area={Grid.navbar} className='bg-gray-900 text-gray-100 overflow-y-auto'>
            <Navbar />
          </Item>
          <Item area={Grid.content} className='overflow-y-auto py-8'>
            <Switch>
${
        types.keySet().joinToString(separator = "\n") { typeName ->
            """              <Route exact path='/${
                "${
                    typeName.subSequence(0, 1).toString().toLowerCase()
                }${typeName.substring(1)}".split("(?=\\p{Upper})".toRegex()).joinToString(separator = "-") {
                    "${
                        it.subSequence(0, 1).toString().toLowerCase()
                    }${it.substring(1)}"
                }
            }-list'><List${typeName} /></Route>
              <Route exact path='/${
                "${
                    typeName.subSequence(0, 1).toString().toLowerCase()
                }${typeName.substring(1)}".split("(?=\\p{Upper})".toRegex()).joinToString(separator = "-") {
                    "${
                        it.subSequence(0, 1).toString().toLowerCase()
                    }${it.substring(1)}"
                }
            }/*'><Show${typeName} /></Route>
              <Route exact path='/${
                "${
                    typeName.subSequence(0, 1).toString().toLowerCase()
                }${typeName.substring(1)}".split("(?=\\p{Upper})".toRegex()).joinToString(separator = "-") {
                    "${
                        it.subSequence(0, 1).toString().toLowerCase()
                    }${it.substring(1)}"
                }
            }'><Show${typeName} /></Route>"""
        }
    }
              <Route path='/'><Redirect to='/regions' /></Route>
            </Switch>
          </Item>
        </Container>
      </BrowserRouter>
    </div>
  )
}

export default App

"""
}

fun main() {
    createDirectories(Path.of("pibity-erp/src/main"))
    File("pibity-erp/src/main/App.tsx").writeText(getAppTs())
    File("pibity-erp/src/main/circuits.ts").writeText(getCircuitsTs())
    File("pibity-erp/src/main/mapper.ts").writeText(getMappersTs())
    File("pibity-erp/src/main/dexie.ts").writeText(getDexieTs())
    File("pibity-erp/src/main/mutation.ts").writeText(getMutationTs())
    File("pibity-erp/src/main/functions.ts").writeText(getFunctionsTs())
    File("pibity-erp/src/main/layers.ts").writeText(getLayersTs())
    File("pibity-erp/src/main/types.ts").writeText(getTypesTs())
    File("pibity-erp/src/main/rows.ts").writeText(getRowsTs())
    File("pibity-erp/src/main/rows.ts").appendText(getRowsTsDiffRow())
    File("pibity-erp/src/main/variables.ts").writeText(getVariablesTs())
    File("pibity-erp/src/main/variables.ts").appendText(getVariablesTsReplaceVariable())
    types.entrySet().forEach { (typeName, typeDef) ->
        createDirectories(Path.of("pibity-erp/src/components/${typeDef.asJsonObject.get("group").asString}/${typeName}/grids"))
        File("pibity-erp/src/components/${typeDef.asJsonObject.get("group").asString}/${typeName}/grids/Show.ts").writeText(getShowGridTs(typeDef.asJsonObject))
        File("pibity-erp/src/components/${typeDef.asJsonObject.get("group").asString}/${typeName}/grids/List.ts").writeText(getListGridTs())
        File("pibity-erp/src/components/${typeDef.asJsonObject.get("group").asString}/${typeName}/Show.tsx").writeText(getShowTs(typeName, typeDef.asJsonObject))
        File("pibity-erp/src/components/${typeDef.asJsonObject.get("group").asString}/${typeName}/List.tsx").writeText(getListTs(typeName, typeDef.asJsonObject))
    }
}
