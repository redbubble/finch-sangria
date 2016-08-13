package com.redbubble.graphql

import com.redbubble.graphql.GraphQlError._
import com.redbubble.graphql.GraphQlQueryDecoders._
import io.finch.Output.payload
import io.finch.{Endpoint, _}

trait GraphQlEndpoint {
  // Note. Used to provide a `graphqlQuery` combinator that can be used in `GET` requests.
  final val graphqlQuery: Endpoint[GraphQlQuery] = {
    val paramsTupled = (param("query") :: paramOption("variables") :: paramOption("operationName")).asTuple
    paramsTupled.mapOutput { params =>
      buildGraphQlQuery(params._1, params._2, params._3).fold(
        e => BadRequest(graphQlError("Error parsing GraphQL query", e)),
        q => payload(q)
      )
    }
  }
}

object GraphQlEndpoint extends GraphQlEndpoint
