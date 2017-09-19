[![Build status](https://img.shields.io/travis/redbubble/finch-sangria/master.svg)](https://travis-ci.org/redbubble/finch-sangria)

# Finch GraphQL support

Some simple wrappers around [Sangria](http://sangria-graphql.org) to support its use in [Finch](https://github.com/finagle/finch).

It is a small layer, that is reasonably opininated, which may not be to your liking. In particular:

* We transport GraphQL queries as JSON, over HTTP. This necessitates some nasties from time to time.
* We use Twitter classes instead of the standard library, for things like `Future` and `Try`.
* We use `Future`s containing `Option`s or `Either`s instead a failing `Future`. Failing `Future`s are only used for
  things that we'd not reasonably expect a client to be able to handle (i.e. something catastrophic).
* We handle variables in the form of a JSON encoded string (for example from GraphiQL), as well as a straight JSON object.
* We do our best to give back semi-sane HTTP status codes.
* We expect that you want strong types for things.

There are some things that need improvement, including:

* We are hard coded to Circe, it should be fairly easy to decouple it should you so wish.
* In the same vein, the executor returns `Json`, mainly because of the `CirceResultMarshaller`. Ideally both of these
  would use some form of class that represented the variables/results, and defined an `InputUnmarshaller` and a
  `ResultMarshaller` for them respectively. In particular, this leads to the unpleasantness with the re-parsing of the
  JSON returned from the underlying executor to find the status of the result.

If you like this, you might like other open source code from Redbubble:

* [rb-scala-utils](https://github.com/redbubble/rb-scala-utils) - Miscellaneous utilities (common code) for building
  Scala-based services, using Finch (on which this project depends).
* [finch-template](https://github.com/redbubble/finch-template) - A template project for Finch-based services.
* [rb-graphql-template](https://github.com/redbubble/rb-graphql-template) - A template for Scala HTTP GraphQL services.
* [finagle-hawk](https://github.com/redbubble/finagle-hawk) - HTTP Holder-Of-Key Authentication Scheme for Finagle.

# Setup

You will need to add something like the following to your `build.sbt`:

```scala
resolvers += Resolver.jcenterRepo

libraryDependencies += "com.redbubble" %% "finch-sangria" % "0.3.2"
```

# Usage

1. Configure the executor:

    ```scala
    val schema = ...           // your Sangria schema
    val context = ...          // your root context
    val errorReporter = ...    // a way to log errors, e.g. Rollbar
    val serverMetrics = ...    // your stats receiver
    val logger = ...           // a logger

    val executor = GraphQlQueryExecutor.executor(
      schema, context, maxQueryDepth = 10)(errorReporter, serverMetrics, logger)
    ```

  Set the max depth to whatever suits your schema (you'll likely need >= 10 for the introspection query).

1. Write your endpoint:

    ```scala
    import com.redbubble.graphql.GraphQlRequestDecoders.graphQlQueryDecode

    object GraphQlApi {
      val stats = StatsReceiver.stats

      def graphQlGet: Endpoint[Json] =
        get("graphql" :: graphqlQuery) { query: GraphQlQuery =>
          executeQuery(query)
        }

      def graphQlPost: Endpoint[Json] =
        post("graphql" :: jsonBody[GraphQlQuery]) { query: GraphQlQuery =>
          executeQuery(query)
        }

      private def executeQuery(query: GraphQlQuery): Future[Output[Json]] = {
        val operationName = query.operationName.getOrElse("unnamed_operation")
        stats.counter("count", operationName).incr()
        Stat.timeFuture(stats.stat("execution_time", operationName)) {
          runQuery(query)
        }
      }

      private def runQuery(query: GraphQlQuery): Future[Output[Json]] = {
        val result = executor.execute(query)(globalAsyncExecutionContext)

        // Do our best to map the type of error back to a HTTP status code
        result.map {
          case SuccessfulGraphQlResult(json) => Output.payload(json, Status.Ok)
          case ClientErrorGraphQlResult(json, _) => Output.payload(json, Status.BadRequest)
          case BackendErrorGraphQlResult(json, _) => Output.payload(json, Status.InternalServerError)
        }
      }
    }
    ```

1. Bring the response encoder into scope when you create your `Service`:

    ```scala
    import com.redbubble.graphql.GraphQlEncoders.graphQlResultEncode

    val api = GraphQlApi.graphQlGet :+: GraphQlApi.graphQlPost
    val service = api.toServiceAs[Application.Json]
    Http.server.serve(":8080", service)
    ```

# GraphiQL

If you want to integrate [GraphiQL](https://github.com/graphql/graphiql) (you should), it's pretty easy.

1. Pull down the latest [GraphiQL file](https://github.com/graphql/graphiql/blob/master/example/index.html).

1. You may need to adjust the paths within the GraphiQL file if you're using versioned paths, etc.

1. Stick it somewhere in your classpath.

1. Write an endpoint for it:

    ```scala
    object ExploreApi {
      private val graphiQlPath = "/graphiql.html"

      def explore: Endpoint[Response] = get("explore") {
        classpathResource(graphiQlPath).map(fromStream) match {
          case Some(content) => asyncHtmlResponse(Status.Ok, AsyncStream.fromReader(content, chunkSize = 512.kilobytes.inBytes.toInt))
          case None => textResponse(Status.InternalServerError, Buf.Utf8(s"Unable to find GraphiQL at '$graphiQlPath'"))
        }
      }

	    private def classpathResource(name: String): Option[InputStream] = Option(getClass.getResourceAsStream(name))
    }
    ```

# Other Fun Bits

We've added some other bits & pieces to make using Sangria easier.

## Scalar types

There are various helpers that can help you define Scalar types. For example to add support for a tagged type:

```scala
//
// Set up a tagged type
//

import shapeless.tag
import shapeless.tag._

trait PixelWidthTag
type PixelWidth = Int @@ PixelWidthTag
def PixelWidth(w: Int): @@[Int, PixelWidthTag] = tag[PixelWidthTag](w)

//
// Define your GraphQL type for the tagged type
//

private val widthRange = 1 to MaxImageDimension
private implicit val widthInput = new ScalarToInput[PixelWidth]

private case object WidthCoercionViolation
    extends ValueCoercionViolation(s"Width in pixels, between ${widthRange.start} and ${widthRange.end}")

private def parseWidth(i: Int) = intValueFromInt(i, widthRange, PixelWidth, () => WidthCoercionViolation)

val WidthType = intScalarType(
  "width",
  s"The width of an image, in pixels, between ${widthRange.start} and ${widthRange.end} (default $DefaultImageWidth).",
  parseWidth, () => WidthCoercionViolation)

val WidthArg: Argument[PixelWidth] = Argument(
  name = "width",
  argumentType = OptionInputType(WidthType),
  description = s"The width of an image, in pixels, between ${widthRange.start} and ${widthRange.end} (default $DefaultImageWidth).", defaultValue = DefaultImageWidth)
```

## Input types

We've also added support for input types, in a similar way to how other types are handled, they are typesafe.

```scala
// Tagged type
trait PushNotificationTokenTag
type PushNotificationToken = String @@ PushNotificationTokenTag
def PushNotificationToken(t: String): @@[String, PushNotificationTokenTag] = tag[PushNotificationTokenTag](t)

// GraphQL type
private case object PushNotificationTokenCoercionViolation
    extends ValueCoercionViolation(s"Push notification token expected")

private def parseToken(s: String): Either[PushNotificationTokenCoercionViolation.type, PushNotificationToken] =
  Right(PushNotificationToken(s))

val PushNotificationTokenType =
  stringScalarType(
    "PushNotificationToken", s"An iOS push notification token.",
    parseToken, () => PushNotificationTokenCoercionViolation
  )

val PushNotificationTokenArg =
  Argument("token", PushNotificationTokenType, description = s"An iOS push notification token.")


//
// Input type for our type
//
val FieldPushNotificationToken = InputField(
  "token",
  OptionInputType(PushNotificationTokenType),
  "If available, the push notification token for the device. May be empty if the user has not given permission to send notifications."
)

val RegisterDeviceType: InputObjectType[DefaultInput] =
  InputObjectType(
    name = "RegisterDevice",
    description = "Register device fields.",
    fields = List(FieldPushNotificationToken, FieldBundleId, FieldAppVersion, FieldOsVersion)
  )

val RegisterDeviceArg = Argument(InputFieldName, RegisterDeviceType, "Register device fields.")

//
// Let's use that type in a mutation
//

object DeviceRegistration extends InputHelper {
  def registerDevice(ctx: Context[RootContext, Unit]): Action[RootContext, RegisteredDevice] = {
    val token = ctx.inputArg(FieldPushNotificationToken).flatten
    val registeredDevice = for {
      bundleId <- ctx.inputArg(FieldBundleId)
      appVersion <- ctx.inputArg(FieldAppVersion).flatMap(fromRawVersion)
      osVersion <- ctx.inputArg(FieldOsVersion).flatMap(fromRawVersion)
    } yield {
      val device = Device.device(token, App(bundleId, appVersion), osVersion)
      ctx.ctx.registerDevice(device)
    }
    registeredDevice.getOrElse(Future.exception(graphQlError("Unable to parse device input fields"))).asScala
  }
}

val MutationType: ObjectType[RootContext, Unit] = ObjectType(
  "MutationAPI",
  description = "The Redbubble iOS Mutation API.",
  fields[RootContext, Unit](
    Field(
      name = "registerDevice",
      arguments = List(RegisterDeviceArg),
      fieldType = OptionType(RegisteredDeviceType),
      resolve = registerDevice
    )
  )
)
```

# Release

For contributors, a cheat sheet to making a new release:

```shell
$ git commit -m "New things" && git push
$ git tag -a v0.0.3 -m "v0.0.3"
$ git push --tags
$ ./sbt publish
```

# Contributing

Issues and pull requests are welcome. Code contributions should be aligned with the above scope to be included, and include unit tests.
