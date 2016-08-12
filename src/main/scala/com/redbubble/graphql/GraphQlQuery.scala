package com.redbubble.graphql

import io.circe.Json
import sangria.ast.Document

final case class GraphQlQuery(document: Document, variables: Option[Json], operationName: Option[String] = None)
