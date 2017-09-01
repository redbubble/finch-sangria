package com.redbubble.graphql

import com.redbubble.graphql.util.json.JsonPrinter

object SimpleQueryRenderer {
  def graphqlQuery(q: GraphQlQuery): String = q.document.renderCompact

  def graphqlOperation(q: GraphQlQuery): String = q.operationName.getOrElse("<Empty>")

  def graphqlVariables(q: GraphQlQuery): String = q.variables.map(j => JsonPrinter.jsonToString(j)).getOrElse("<Empty>")
}
