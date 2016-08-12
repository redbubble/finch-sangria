package com.redbubble.graphql

trait GraphQlJsonOps {
  def errorsJson(message: String, field: String, errorLocations: Seq[ErrorLocation]): String =
    s"""
       |{
       |  "errors": [
       |    ${errorWithField(message, field, errorLocations, "AggregateGraphQlError")}
       |  ]
       |}
     """.stripMargin

  def errorWithField(message: String, field: String, errorLocations: Seq[ErrorLocation], errorType: String): String =
    s"""
       |{
       |  "message" : "$message",
       |  "field" : "$field",
       |  "type" : "$errorType",
       |  "locations" : [${errorLocations.map(locationJson).mkString(", ")}]
       |}
      """.stripMargin

  def errorWithPath(message: String, path: Seq[String], errorLocations: Seq[ErrorLocation]): String =
    s"""
       |{
       |  "message" : "$message",
       |  "path" : [${path.map(p => s""""$p"""").mkString(", ")}],
       |  "locations" : [${errorLocations.map(locationJson).mkString(", ")}]
       |}
       |
     """.stripMargin

  def locationJson(errorLocation: ErrorLocation): String =
    s"""{"line" : ${errorLocation.line}, "column" : ${errorLocation.column}}"""
}

object GraphQlJsonOps extends GraphQlJsonOps
