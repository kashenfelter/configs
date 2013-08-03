/*
 * Copyright 2013 Tsukasa Kitachi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.kxbmap.configs

import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import java.net.{InetSocketAddress, InetAddress}
import org.scalacheck.{Gen, Arbitrary}
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.prop.PropertyChecks
import scala.concurrent.duration._
import scala.util.{Try, Success}


class ConfigsSpec extends FlatSpec with ShouldMatchers with PropertyChecks {

  behavior of "Configs"

  it should "be satisfy Functor law" in {
    val c = ConfigFactory.parseString(
      """foo="Hello, World!"
        |bar=42
        |baz=[not string]
      """.stripMargin)

    implicit val configsArb = Arbitrary {
      for {
        p <- Gen.oneOf(Seq("foo", "bar", "baz", "qux", ""))
      } yield Configs.configs { c =>
        try Some(c.getString(p)) catch {
          case _: Throwable => None
        }
      }
    }

    implicit val funArb = Arbitrary[Option[String] => Option[String]] {
      val fs = Seq[String => String](
        _.toUpperCase, _.toLowerCase, _.capitalize, _.reverse, _.sorted, _.tail
      )
      Gen.oneOf(fs).map { f => (_: Option[String]).map(f) }
    }

    forAll { (cs: Configs[Option[String]],
              f: Option[String] => Option[String],
              g: Option[String] => Option[String]) =>

      cs.map(identity).extract(c) should be (identity(cs).extract(c))
      cs.map(f compose g).extract(c) should be (cs.map(g).map(f).extract(c))
    }
  }


  val config = ConfigFactory.load("test-data")

  it should "get a standard value(s)" in {
    config.get[Int]("int.value") should be (42)
    config.get[List[Int]]("int.values") should be (List(23, 42, 256))

    config.get[Long]("long.value") should be (42000000000L)
    config.get[List[Long]]("long.values") should be (List(230000000000L, 42L, 256L))

    config.get[Double]("double.value") should be (42.195d)
    config.get[List[Double]]("double.values") should be (List(2.3d, Double.MinPositiveValue))

    config.get[Boolean]("boolean.value") should be (true)
    config.get[List[Boolean]]("boolean.values") should be (List(true, false))

    config.get[String]("string.value") should be ("foobar")
    config.get[List[String]]("string.values") should be (List("Hello", "World"))
  }

  it should "get a Config(s)" in {
    import scala.collection.JavaConverters._

    val int = ConfigFactory.parseMap(Map(
      "value"   -> Int.box(42),
      "values"  -> List(23, 42, 256).asJava
    ).asJava)

    val string = ConfigFactory.parseMap(Map(
      "value"   -> "foobar",
      "values"  -> List("Hello", "World").asJava
    ).asJava)

    config.get[Config]("int") should be (int)
    config.get[Config]("string") should be (string)
    config.get[List[Config]]("config.values") should be (List(int, string))
  }

  it should "extract a Config" in {
    import scala.collection.JavaConverters._

    config.getConfig("int").extract[Config] should be (
      ConfigFactory.parseMap(Map(
        "value"   -> Int.box(42),
        "values"  -> List(23, 42, 256).asJava
      ).asJava))
  }

  it should "get a String Map(s)" in {
    config.get[Map[String, Int]]("map.value") should be (Map("a" -> 1, "b" -> 2))
    config.get[List[Map[String, Int]]]("map.values") should be (List(
      Map("a" -> 1, "b" -> 2),
      Map("c" -> 1, "d" -> 2, "e" -> 3),
      Map("f" -> 1, "g" -> 2)
    ))
  }

  it should "extract a String Map" in {
    config.getConfig("map.value").extract[Map[String, Int]] should be (Map("a" -> 1, "b" -> 2))
  }

  it should "get a Symbol Map(s)" in {
    config.get[Map[Symbol, Int]]("map.value") should be (Map('a -> 1, 'b -> 2))
    config.get[List[Map[Symbol, Int]]]("map.values") should be (List(
      Map('a -> 1, 'b -> 2),
      Map('c -> 1, 'd -> 2, 'e -> 3),
      Map('f -> 1, 'g -> 2)
    ))
  }

  it should "extract a Symbol Map" in {
    config.getConfig("map.value").extract[Map[Symbol, Int]] should be (Map('a -> 1, 'b -> 2))
  }

  it should "get a Symbol(s)" in {
    config.get[Symbol]("string.value") should be === ('foobar)
    config.get[List[Symbol]]("string.values") should be (List('Hello, 'World))
  }

  it should "get duration(s)" in {
    config.get[Duration]("duration.value") should be (10.days)
    config.get[List[Duration]]("duration.values") should be (List(1.milli, 42.hours, 1.second))
  }

  it should "get as Option" in {
    import ShouldCatch.Implicit.configException
    config.opt[Int]("int.value") should be (Some(42))
    config.opt[List[Int]]("int.values") should be (Some(List(23, 42, 256)))
    config.opt[Int]("string.value") should be (None)
    config.opt[Int]("missing.value") should be (None)
  }

  it should "get or missing as Option" in {
    intercept[ConfigException.WrongType] {
      config.opt[Int]("string.value")
    }
    config.opt[Int]("missing.value") should be (None)
  }

  it should "get or default value" in {
    import ShouldCatch.Implicit.configException
    config.getOrElse("int.value", 0) should be (42)
    config.getOrElse("string.value", 0) should be (0)
  }

  it should "get as Either" in {
    {
      import ShouldCatch.Implicit.nonFatal
      config.either[Int]("int.value") should be (Right(42))
      config.either[List[Int]]("int.values") should be (Right(List(23, 42, 256)))
    }
    {
      import ShouldCatch.Implicit.configException
      config.either[Int]("string.value") should be ('left)
      config.either[Int]("missing.value") should be ('left)
    }
    intercept[ConfigException.WrongType] {
      config.either[Int]("string.value")
    }
  }

  it should "get as Try" in {
    config.get[Try[Int]]("int.value") should be (Success(42))
    config.get[Try[Int]]("string.value") should be ('failure)
    config.get[Try[Int]]("missing.value") should be ('failure)
  }

  it should "not suppress fatal errors" in {
    implicit val fatal = Configs.atPath[String] { (_, _) => throw new NotImplementedError() }
    import ShouldCatch.Implicit.nonFatal

    intercept[NotImplementedError] {
      config.opt[String]("string.value")
    }

    intercept[NotImplementedError] {
      config.either[String]("string.value")
    }

    intercept[NotImplementedError] {
      config.get[Try[String]]("string.value")
    }
  }

  it should "get value(s) via user defined instances" in {
    implicit val inetAddressAtPath      = AtPath by InetAddress.getByName
    implicit val inetAddressListAtPath  = AtPath listBy InetAddress.getByName

    config.get[InetAddress]("address.value") should be (InetAddress.getByName("127.0.0.1"))
    config.get[List[InetAddress]]("address.values") should be (List(
      InetAddress.getByName("127.0.0.1"),
      InetAddress.getByName("localhost"),
      InetAddress.getByName("::1")
    ))
    config.get[Map[String, InetAddress]]("address.map") should be (Map(
      "1" -> InetAddress.getByName("127.0.0.1"),
      "2" -> InetAddress.getByName("localhost"),
      "3" -> InetAddress.getByName("::1")
    ))

    implicit val inetSocketAddressConfigs = Configs.configs[InetSocketAddress] { c =>
      new InetSocketAddress(c.get[InetAddress]("addr"), c.get[Int]("port"))
    }

    config.get[InetSocketAddress]("socket.address.value") should be (new InetSocketAddress("127.0.0.1", 80))
    config.get[List[InetSocketAddress]]("socket.address.values") should be (List(
      new InetSocketAddress("127.0.0.1", 80),
      new InetSocketAddress("localhost", 9000),
      new InetSocketAddress("::1", 65535)
    ))

    config.get[Map[String, Option[InetSocketAddress]]]("socket.address.map") should be (Map(
      "1" -> Some(new InetSocketAddress("127.0.0.1", 80)),
      "2" -> Some(new InetSocketAddress("localhost", 9000)),
      "3" -> Some(new InetSocketAddress("::1", 65535)),
      "4" -> None
    ))
  }

}
