package com.redbubble.graphql

import sangria.schema.InputObjectType.DefaultInput
import sangria.schema.{InputField, WithArguments}

trait InputHelper {
  val InputFieldName = "input"

  def value[T](ctx: WithArguments, field: InputField[T]): Option[T] =
    inputParams(ctx).flatMap(ps => ps.get(field.name).map(value1 => value1.asInstanceOf[T]))

  private def inputParams(ctx: WithArguments): Option[DefaultInput] =
    ctx.args.raw.get(InputFieldName).map(_.asInstanceOf[DefaultInput])
}
