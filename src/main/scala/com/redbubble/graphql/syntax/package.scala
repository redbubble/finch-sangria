package com.redbubble.graphql

import sangria.schema.{InputField, WithArguments}

package object syntax extends InputHelper {

  implicit final class RichWithArguments(val a: WithArguments) extends AnyVal {
    def inputArg[T](field: InputField[T]): Option[T] = value[T](a, field)

    def inputArgNamed[T](paramName: String, field: InputField[T]): Option[T] = valueNamed[T](a, paramName, field)
  }

}
