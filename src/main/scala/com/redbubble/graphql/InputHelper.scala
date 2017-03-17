package com.redbubble.graphql

import sangria.schema.InputObjectType.DefaultInput
import sangria.schema.{InputField, WithArguments}

trait InputHelper {
  final val InputFieldName = "input"

  def value[T](ctx: WithArguments, field: InputField[T]): Option[T] = valueNamed(ctx, InputFieldName, field)

  def valueNamed[T](ctx: WithArguments, paramName: String, field: InputField[T]): Option[T] =
    inputParams(ctx, paramName).flatMap(_.get(field.name).map(_.asInstanceOf[T]))

  private def inputParams(ctx: WithArguments, paramName: String): Option[DefaultInput] =
    ctx.args.raw.get(paramName).map(_.asInstanceOf[DefaultInput])
}
