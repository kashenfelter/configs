/*
 * Copyright 2013-2016 Tsukasa Kitachi
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

package configs

import com.typesafe.config.{Config, ConfigValue}
import scala.collection.generic.CanBuildFrom


trait Configs[A] {

  def get(config: Config, path: String): Attempt[A]

  def extract(config: Config): Attempt[A] =
    get(config.atKey(Configs.ExtractKey), Configs.ExtractKey)

  def extract(value: ConfigValue): Attempt[A] =
    get(value.atKey(Configs.ExtractKey), Configs.ExtractKey)

  def map[B](f: A => B): Configs[B] =
    (c, p) => get(c, p).map(f)

  def mapF[F[_], B, C](f: B => C)(implicit ev1: A =:= F[B], ev2: F[B] => Traversable[B], cbf: CanBuildFrom[Nothing, C, F[C]]): Configs[F[C]] =
    map(ev1(_).map(f)(collection.breakOut))

  def flatMap[B](f: A => Configs[B]): Configs[B] =
    (c, p) => get(c, p).flatMap(f(_).get(c, p))

  def orElse[B >: A](fallback: Configs[B]): Configs[B] =
    (c, p) => get(c, p).orElse(fallback.get(c, p))

}

object Configs extends ConfigsInstances {

  private final val ExtractKey = "configs-extract"

  @inline
  def apply[A](implicit A: Configs[A]): Configs[A] = A


  def of[A]: Configs[A] =
    macro macros.ConfigsMacro.materialize[A]

  def bean[A]: Configs[A] =
    macro macros.BeanConfigsMacro.materializeA[A]

  def bean[A](newInstance: => A): Configs[A] =
    macro macros.BeanConfigsMacro.materializeI[A]


  def from[A](f: (Config, String) => A): Configs[A] =
    (c, p) => Attempt(f(c, p))

  def attempt[A](f: (Config, String) => Attempt[A]): Configs[A] =
    (c, p) => Attempt(f(c, p)).flatten

  def onPath[A](f: Config => A): Configs[A] =
    from((c, p) => f(c.getConfig(p)))

  def attemptOnPath[A](f: Config => Attempt[A]): Configs[A] =
    attempt((c, p) => f(c.getConfig(p)))


  final class MapF[F[_], A, B] private(c: Configs[F[A]])(implicit ev: F[A] => Traversable[A], cbf: CanBuildFrom[Nothing, B, F[B]]) {
    def apply(f: A => B): Configs[F[B]] = c.mapF(f)
  }

  object MapF {
    implicit def mkMapF[F[_], A, B](implicit c: Configs[F[A]], ev: F[A] => Traversable[A], cbf: CanBuildFrom[Nothing, B, F[B]]): MapF[F, A, B] =
      new MapF(c)
  }

}
