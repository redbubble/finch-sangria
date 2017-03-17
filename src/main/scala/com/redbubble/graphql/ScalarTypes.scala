package com.redbubble.graphql

import cats.syntax.either._
import mouse.boolean._
import sangria.ast
import sangria.schema.ScalarType
import sangria.validation.ValueCoercionViolation

object ScalarTypes {
  def intValueFromInt[V, E <: ValueCoercionViolation](
      i: Int, validValues: Range, value: Int => V, error: () => E): Either[E, V] =
    validValues.contains(i).either(error, value(i)).leftMap(_ => error())

  /**
    * A scalar type for strongly typed (or tagged type) wrappers (wrapper type `V`) around `String`s.
    *
    * @param typeName    The name of the type.
    * @param description The description (human readable) of the type.
    * @param value       The function to construct a value of this type from a `String`.
    * @param error       The function to use when the value passed is not a `String`.
    */
  def stringScalarType[V, E <: ValueCoercionViolation](
      typeName: String, description: String, value: String => Either[E, V], error: () => E): ScalarType[V] =
    ScalarType[V](typeName,
      coerceUserInput = {
        case s: String => value(s)
        case _ => Left(error())
      },
      coerceInput = {
        case ast.StringValue(s, _, _) => value(s)
        case _ => Left(error())
      },
      coerceOutput = (c, _) => c.toString,
      description = Some(description)
    )

  /**
    * A scalar type for strongly typed (or tagged type) wrappers (`V`) around `Int`s.
    *
    * @param typeName    The name of the type.
    * @param description The description (human readable) of the type.
    * @param value       The function to construct a value of this type from an `Int`.
    * @param error       The function to use when the value passed is not an `Int`.
    */
  def intScalarType[V, E <: ValueCoercionViolation](
      typeName: String, description: String, value: Int => Either[E, V], error: () => E): ScalarType[V] =
    ScalarType[V](typeName,
      coerceUserInput = {
        case i: Int => value(i)
        case i: Long if i.isValidInt => value(i.toInt)
        case i: BigInt if !i.isValidInt => Left(tooBigCoercionViolation(typeName))
        case i: BigInt => value(i.intValue())
        case _ => Left(error())
      },
      coerceInput = {
        case ast.IntValue(i, _, _) => value(i)
        case ast.BigIntValue(i, _, _) if !i.isValidInt => Left(tooBigCoercionViolation(typeName))
        case ast.BigIntValue(i, _, _) => value(i.intValue())
        case _ => Left(error())
      },
      coerceOutput = (v, _) => v,
      description = Some(description)
    )

  private def tooBigCoercionViolation(typeName: String): ValueCoercionViolation =
    new ValueCoercionViolation(s"Value is too big to fit in $typeName") {}
}
