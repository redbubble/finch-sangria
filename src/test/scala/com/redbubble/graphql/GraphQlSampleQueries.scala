package com.redbubble.graphql

import com.redbubble.util.io.BufOps._
import com.redbubble.util.json.CodecOps._
import com.twitter.io.Buf

trait GraphQlSampleQueries {

  implicit private[graphql] final class QuotedString(val s: String) {
    def escapeQuotes: String = s.replace("\"", "\\\"")
  }

  implicit private[graphql] final class QuotedBuf(val b: Buf) {
    def escapeQuotes: Buf = stringToBuf(new QuotedString(bufToString(b)).escapeQuotes)
  }

  val validQueries = List(
    """
      |{
      |  work(work_id: 22344) {
      |    name
      |    artist {
      |      name
      |      profile_url
      |    }
      |  }
      |}""",
    """
      |query IntrospectionQuery {
      |  __schema {
      |    queryType { name }
      |    mutationType { name }
      |    subscriptionType { name }
      |    types {
      |      ...FullType
      |    }
      |    directives {
      |      name
      |      description
      |      args {
      |        ...InputValue
      |      }
      |      onOperation
      |      onFragment
      |      onField
      |    }
      |  }
      |}
      |
      |fragment FullType on __Type {
      |  kind
      |  name
      |  description
      |  fields(includeDeprecated: true) {
      |    name
      |    description
      |    args {
      |      ...InputValue
      |    }
      |    type {
      |      ...TypeRef
      |    }
      |    isDeprecated
      |    deprecationReason
      |  }
      |  inputFields {
      |    ...InputValue
      |  }
      |  interfaces {
      |    ...TypeRef
      |  }
      |  enumValues(includeDeprecated: true) {
      |    name
      |    description
      |    isDeprecated
      |    deprecationReason
      |  }
      |  possibleTypes {
      |    ...TypeRef
      |  }
      |}
      |
      |fragment InputValue on __InputValue {
      |  name
      |  description
      |  type { ...TypeRef }
      |  defaultValue
      |}
      |
      |fragment TypeRef on __Type {
      |  kind
      |  name
      |  ofType {
      |    kind
      |    name
      |    ofType {
      |      kind
      |      name
      |      ofType {
      |        kind
      |        name
      |      }
      |    }
      |  }
      |}""",
    """
      |# `me` could represent the currently logged in viewer.
      |{
      |  me {
      |    name
      |  }
      |}""",
    """query ff {
      |  foo {
      |    name
      |  }
      |}""",
    """
      |query withFragments {
      |  user(id: 4) {
      |    friends(first: 10) {
      |      ...friendFields
      |    }
      |    mutualFriends(first: 10) {
      |      ...friendFields
      |    }
      |  }
      |}
      |
      |fragment friendFields on User {
      |  id
      |  name
      |  profilePic(size: 50)
      |}
    """,
    """
      |query FetchPlaylist {
      |  playlist(id: "e66637db-13f9-4056-abef-f731f8b1a3c7") {
      |    id
      |    name
      |
      |    tracks {
      |      id
      |      title
      |      viewerHasLiked
      |    }
      |  }
      |}
    """,
    """
      |query findUser($userId: String!) {
      |  findUser(id: $userId) {
      |    name
      |  }
      |}
    """.stripMargin
  ).map(_.stripMargin)
  val invalidQueries = List(
    """{s dfsd1!fs/d{{ |  } |}""",
    """{}""",
    "# this is a comment"
  ).map(_.stripMargin)
  val variableStrings = List(
    "{\n \"null\": null\n}",
    "{\n \"boolean\": false\n}",
    "{\n \"integer\": 1\n}",
    "{\n \"float\": 123.0\n}",
    "{\n \"string\": \"bar\"\n}",
    "{\n \"array_of_boolean\": true\n}",
    "{\n \"array_of_numbers\": [1, 2, 3]\n}",
    "{\n \"array_of_string\": \"bar\"\n}",
    "{\n \"array_of_array\": [\"bar\", \"baz\", \"quux\"]\n}",
    "{\n \"object_with_object\": { \"bar\": \"baz\", \"quux\": 123, \"numbers\": [1, 2, 3] }\n}"
  )
  val invalidVariableStrings = List(
    "",
    "fddsfsd",
    "||",
    "[1, 2, 3]",
    "1",
    "1.0",
    "\"foo\""
  )
  val variableJsons = variableStrings.map(v => parse(v.escapeQuotes).getOrElse(emptyJsonObject))

  def queryJsonPayload(query: String): Buf = stringToBuf(s"""{"query":"${query.escapeQuotes}"}""")

  def queryJsonPayloadStrings(query: String, variables: String): Buf =
    stringToBuf(s"""{"query":"${query.escapeQuotes}","variables":"${variables.escapeQuotes}"}""")

  def queryJsonPayloadObject(query: String, variables: String): Buf =
    stringToBuf(s"""{"query":"${query.escapeQuotes}","variables":$variables}""")

  def queryJsonPayload(query: String, variables: String, operation: String): Buf =
    stringToBuf(s"""{"query":"${query.escapeQuotes}","variables":"${variables.escapeQuotes}","operationName":"$operation"}""")
}

object GraphQlSampleQueries extends GraphQlSampleQueries
