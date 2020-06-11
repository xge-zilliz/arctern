/*
 * Copyright (C) 2019-2020 Zilliz. All rights reserved.
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
package org.apache.spark.sql.arctern.expressions

import org.apache.spark.sql.arctern.{ArcternExpr, CodeGenUtil, GeometryUDT}
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.types.{ArrayType, ByteType, DataType, NumericType, StringType}
import org.apache.spark.sql.catalyst.expressions.codegen._
import org.apache.spark.sql.catalyst.expressions.codegen.Block._
import org.apache.spark.unsafe.types.UTF8String

case class ST_GeomFromText(inputExpr: Seq[Expression]) extends ArcternExpr {

  assert(inputExpr.length == 1)
  assert(inputExpr.head.dataType match { case _: StringType => true })

  override def nullable: Boolean = true

  override def eval(input: InternalRow): Any = {}

  override protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode = {

    val wktExpr = inputExpr.head
    val wktGen = inputExpr.head.genCode(ctx)

    val nullSafeEval =
      wktGen.code + ctx.nullSafeExec(wktExpr.nullable, wktGen.isNull) {
        s"""
           |${ev.value}_geo = ${GeometryUDT.getClass().getName().dropRight(1)}.FromWkt(${wktGen.value}.toString());
           |if (${ev.value}_geo != null) ${ev.value} = ${CodeGenUtil.serialGeometryCode(s"${ev.value}_geo")}
       """.stripMargin
      }
    ev.copy(code =
      code"""
          ${CodeGenUtil.mutableGeometryInitCode(ev.value + "_geo")}
          ${CodeGenerator.javaType(ArrayType(ByteType, containsNull = false))} ${ev.value} = ${CodeGenerator.defaultValue(dataType)};
          $nullSafeEval
          boolean ${ev.isNull} = (${ev.value}_geo == null);
            """)

  }

  override def dataType: DataType = new GeometryUDT

  override def children: Seq[Expression] = inputExpr
}

case class ST_Point(inputExpr: Seq[Expression]) extends ArcternExpr {

  assert(inputExpr.length == 2)
  assert(inputExpr.head.dataType match { case _: NumericType => true })
  assert(inputExpr(1).dataType match { case _: NumericType => true })

  override def nullable: Boolean = true

  override def eval(input: InternalRow): Any = {}

  override protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode = {

    val xExpr = inputExpr.head
    val yExpr = inputExpr(1)
    val xGen = inputExpr.head.genCode(ctx)
    val yGen = inputExpr(1).genCode(ctx)

    val nullSafeEval =
      xGen.code + ctx.nullSafeExec(xExpr.nullable, xGen.isNull) {
        yGen.code + ctx.nullSafeExec(yExpr.nullable, yGen.isNull) {
          s"""
             |${ev.value}_point = new org.locationtech.jts.geom.GeometryFactory().createPoint(new org.locationtech.jts.geom.Coordinate(${xGen.value},${yGen.value}));
             |if (${ev.value}_point != null) ${ev.value} = ${CodeGenUtil.serialGeometryCode(s"${ev.value}_point")}
          """.stripMargin
        }
      }

    ev.copy(code =
      code"""
          org.locationtech.jts.geom.Point ${ev.value}_point = null;
          ${CodeGenerator.javaType(ArrayType(ByteType, containsNull = false))} ${ev.value} = ${CodeGenerator.defaultValue(dataType)};
          $nullSafeEval
          boolean ${ev.isNull} = (${ev.value}_point == null);
          """)
  }

  override def dataType: DataType = new GeometryUDT

  override def children: Seq[Expression] = inputExpr
}

case class ST_PolygonFromEnvelope(inputExpr: Seq[Expression]) extends ArcternExpr {

  assert(inputExpr.length == 4)
  assert(inputExpr.head.dataType match { case _: NumericType => true })
  assert(inputExpr(1).dataType match { case _: NumericType => true })
  assert(inputExpr(2).dataType match { case _: NumericType => true })
  assert(inputExpr(3).dataType match { case _: NumericType => true })

  override def nullable: Boolean = true

  override def eval(input: InternalRow): Any = {}

  override protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode = {

    val minXExpr = inputExpr.head
    val minYExpr = inputExpr(1)
    val maxXExpr = inputExpr(2)
    val maxYExpr = inputExpr(3)
    val minXGen = inputExpr.head.genCode(ctx)
    val minYGen = inputExpr(1).genCode(ctx)
    val maxXGen = inputExpr(2).genCode(ctx)
    val maxYGen = inputExpr(3).genCode(ctx)

    val nullSafeEval =
      minXGen.code + ctx.nullSafeExec(minXExpr.nullable, minXGen.isNull) {
        minYGen.code + ctx.nullSafeExec(minYExpr.nullable, minYGen.isNull) {
          maxXGen.code + ctx.nullSafeExec(maxXExpr.nullable, maxXGen.isNull) {
            maxYGen.code + ctx.nullSafeExec(maxYExpr.nullable, maxYGen.isNull) {
              s"""
                 |org.locationtech.jts.geom.Coordinate[] coordinates = new org.locationtech.jts.geom.Coordinate[5];
                 |coordinates[0] = new org.locationtech.jts.geom.Coordinate(${minXGen.value}, ${minYGen.value});
                 |coordinates[1] = new org.locationtech.jts.geom.Coordinate(${minXGen.value}, ${maxYGen.value});
                 |coordinates[2] = new org.locationtech.jts.geom.Coordinate(${maxXGen.value}, ${maxYGen.value});
                 |coordinates[3] = new org.locationtech.jts.geom.Coordinate(${maxXGen.value}, ${minYGen.value});
                 |coordinates[4] = coordinates[0];
                 |${ev.value}_polygon = new org.locationtech.jts.geom.GeometryFactory().createPolygon(coordinates);
                 |if (${ev.value}_polygon != null) ${ev.value} = ${CodeGenUtil.serialGeometryCode(s"${ev.value}_polygon")}
              """.stripMargin
            }
          }
        }
      }

    ev.copy(code =
      code"""
          org.locationtech.jts.geom.Polygon ${ev.value}_polygon = null;
          ${CodeGenerator.javaType(ArrayType(ByteType, containsNull = false))} ${ev.value} = ${CodeGenerator.defaultValue(dataType)};
          $nullSafeEval
          boolean ${ev.isNull} = (${ev.value}_polygon == null);
          """)
  }

  override def dataType: DataType = new GeometryUDT

  override def children: Seq[Expression] = inputExpr
}

case class ST_GeomFromGeoJSON(inputExpr: Seq[Expression]) extends ArcternExpr {

  assert(inputExpr.length == 1)
  assert(inputExpr.head.dataType match { case _: StringType => true })

  override def nullable: Boolean = true

  override def eval(input: InternalRow): Any = {}

  override protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode = {

    val jsonExpr = inputExpr.head
    val jsonGen = inputExpr.head.genCode(ctx)

    val nullSafeEval =
      jsonGen.code + ctx.nullSafeExec(jsonExpr.nullable, jsonGen.isNull) {
        s"""
           |${ev.value}_geo = new org.wololo.jts2geojson.GeoJSONReader().read(${jsonGen.value}.toString());
           |if (${ev.value}_geo != null) ${ev.value} = ${CodeGenUtil.serialGeometryCode(s"${ev.value}_geo")}
       """.stripMargin
      }
    ev.copy(code =
      code"""
          ${CodeGenUtil.mutableGeometryInitCode(ev.value + "_geo")}
          ${CodeGenerator.javaType(ArrayType(ByteType, containsNull = false))} ${ev.value} = ${CodeGenerator.defaultValue(dataType)};
          $nullSafeEval
          boolean ${ev.isNull} = (${ev.value}_geo == null);
            """)

  }

  override def dataType: DataType = new GeometryUDT

  override def children: Seq[Expression] = inputExpr
}

case class ST_AsText(inputExpr: Seq[Expression]) extends ArcternExpr {

  assert(inputExpr.length == 1)
  assert(inputExpr.head.dataType match { case _: GeometryUDT => true })

  override def nullable: Boolean = true

  override def eval(input: InternalRow): Any = {}

  override protected def doGenCode(ctx: CodegenContext, ev: ExprCode): ExprCode = {
    val geoExpr = inputExpr.head
    val geoGen = inputExpr.head.genCode(ctx)

    val nullSafeEval =
      geoGen.code + ctx.nullSafeExec(geoExpr.nullable, geoGen.isNull) {
        s"""
           |${ev.value}_geo = ${CodeGenUtil.deserializeGeometryCode(s"${geoGen.value}")}
           |${ev.value}_wkt = ${GeometryUDT.getClass.getName.dropRight(1)}.ToWkt(${ev.value}_geo);
           |if (${ev.value}_wkt != null) ${ev.value} = org.apache.spark.unsafe.types.UTF8String.fromString(${ev.value}_wkt);
       """.stripMargin
      }

    ev.copy(code =
      code"""
          ${CodeGenUtil.mutableGeometryInitCode(ev.value + "_geo")}
          String ${ev.value}_wkt = null;
          ${CodeGenerator.javaType(dataType)} ${ev.value} = ${CodeGenerator.defaultValue(dataType)};
          $nullSafeEval
          boolean ${ev.isNull} = (${ev.value} == null);
            """)

  }

  override def dataType: DataType = StringType

  override def children: Seq[Expression] = inputExpr
}
