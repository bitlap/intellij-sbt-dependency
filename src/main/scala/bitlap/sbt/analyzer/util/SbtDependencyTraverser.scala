package bitlap.sbt.analyzer.util;

import scala.annotation.tailrec

import org.jetbrains.plugins.scala.extensions.&
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.{ ScalaElementVisitor, ScalaPsiElement }
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

import com.intellij.psi.{ PsiElement, PsiFile }

// copy from https://github.com/JetBrains/intellij-scala/blob/idea242.x/sbt/sbt-impl/src/org/jetbrains/sbt/language/utils/SbtDependencyTraverser.scala
// we have changed some
object SbtDependencyTraverser {

  def traverseStringLiteral(stringLiteral: ScStringLiteral)(callback: PsiElement => Boolean): Unit =
    callback(stringLiteral)

  def traverseInfixExpr(infixExpr: ScInfixExpr)(callback: PsiElement => Boolean): Unit = {
    if (!callback(infixExpr)) return

    def traverse(expr: ScExpression): Unit = {
      expr match {
        case subInfix: ScInfixExpr          => traverseInfixExpr(subInfix)(callback)
        case call: ScMethodCall             => traverseMethodCall(call)(callback)
        case refExpr: ScReferenceExpression => traverseReferenceExpr(refExpr)(callback)
        case stringLiteral: ScStringLiteral => traverseStringLiteral(stringLiteral)(callback)
        case blockExpr: ScBlockExpr         => traverseBlockExpr(blockExpr)(callback)
        case _                              =>
      }
    }

    infixExpr.operation.refName match {
      case "++" =>
        traverse(infixExpr.left)
        traverse(infixExpr.right)
      case "++=" | ":=" | "+=" =>
        traverse(infixExpr.right)
      case "%" | "%%" =>
        traverse(infixExpr.left)
        traverse(infixExpr.right)
      case _ =>
        traverse(infixExpr.left)
    }
  }

  def traverseReferenceExpr(refExpr: ScReferenceExpression)(callback: PsiElement => Boolean): Unit = {
    if (!callback(refExpr)) return

    refExpr.resolve() match {
      case (_: ScReferencePattern) & inNameContext(ScPatternDefinition.expr(expr)) =>
        expr match {
          case infix: ScInfixExpr =>
            traverseInfixExpr(infix)(callback)
          case re: ScReferenceExpression =>
            traverseReferenceExpr(re)(callback)
          case seq: ScMethodCall if seq.deepestInvokedExpr.textMatches(SbtDependencyUtils.SEQ) =>
            traverseSeq(seq)(callback)
          case stringLiteral: ScStringLiteral =>
            traverseStringLiteral(stringLiteral)(callback)
          case _ =>
        }
      case _ =>
    }
  }

  def traverseMethodCall(call: ScMethodCall)(callback: PsiElement => Boolean): Unit = {
    if (!callback(call)) return

    call match {
      case seq if seq.deepestInvokedExpr.textMatches(SbtDependencyUtils.SEQ) =>
        traverseSeq(seq)(callback)
      case settings =>
        settings.getEffectiveInvokedExpr match {
          case expr: ScReferenceExpression if expr.refName == SbtDependencyUtils.SETTINGS =>
            traverseSettings(settings)(callback)
          case _ =>
        }
    }
  }

  def traversePatternDef(patternDef: ScPatternDefinition)(callback: PsiElement => Boolean): Unit = {
    if (!callback(patternDef)) return

    val maybeTypeName = patternDef
      .`type`()
      .toOption
      .map(_.canonicalText)

    if (
      maybeTypeName.contains(SbtDependencyUtils.SBT_PROJECT_TYPE) || maybeTypeName.contains(
        SbtDependencyUtils.SBT_CROSS_SETTING_TYPE
      )
    ) {
      retrieveSettings(patternDef).foreach(traverseMethodCall(_)(callback))
    } else {
      patternDef.expr match {
        case Some(call: ScMethodCall)     => traverseMethodCall(call)(callback)
        case Some(infix: ScInfixExpr)     => traverseInfixExpr(infix)(callback)
        case Some(blockExpr: ScBlockExpr) => traverseBlockExpr(blockExpr)(callback)
        case _                            =>
      }
    }
  }

  def traverseSeq(seq: ScMethodCall)(callback: PsiElement => Boolean): Unit = {
    if (!callback(seq)) return

    seq.argumentExpressions.foreach {
      case infixExpr: ScInfixExpr =>
        traverseInfixExpr(infixExpr)(callback)
      case refExpr: ScReferenceExpression =>
        traverseReferenceExpr(refExpr)(callback)
      case _ =>
    }
  }

  def traverseBlockExpr(blockExpr: ScBlockExpr)(callback: PsiElement => Boolean): Unit = {
    if (!callback(blockExpr)) return

    blockExpr.acceptChildren(new ScalaElementVisitor {
      override def visitInfixExpression(infix: ScInfixExpr): Unit = {
        traverseInfixExpr(infix)(callback)
      }

      override def visitMethodCallExpression(call: ScMethodCall): Unit = {
        if (call.deepestInvokedExpr.textMatches(SbtDependencyUtils.SEQ))
          traverseSeq(call)(callback)
      }

      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        traverseReferenceExpr(ref)(callback)
      }
    })
  }

  def traverseSettings(settings: ScMethodCall)(callback: PsiElement => Boolean): Unit = {
    if (!callback(settings)) return

    settings.args.exprs.foreach {
      case infix: ScInfixExpr
          if (infix.left.textMatches(SbtDependencyUtils.LIBRARY_DEPENDENCIES) &&
            SbtDependencyUtils.isAddableLibraryDependencies(infix)) =>
        traverseInfixExpr(infix)(callback)
      case refExpr: ScReferenceExpression => traverseReferenceExpr(refExpr)(callback)
      case _                              =>
    }
  }

  @tailrec
  def retrievePatternDef(psiElement: PsiElement): ScPatternDefinition = {
    psiElement match {
      case patternDef: ScPatternDefinition => patternDef
      case _: PsiFile                      => null
      case _                               => retrievePatternDef(psiElement.getParent)
    }
  }

  def retrieveSettings(patternDef: ScPatternDefinition): Seq[ScMethodCall] = {
    var res: Seq[ScMethodCall] = Seq.empty

    def traverse(pd: ScalaPsiElement): Unit = {
      pd.acceptChildren(new ScalaElementVisitor {
        override def visitMethodCallExpression(call: ScMethodCall): Unit = {
          call.getEffectiveInvokedExpr match {
            case expr: ScReferenceExpression if expr.refName == SbtDependencyUtils.SETTINGS =>
              res ++= Seq(call)
            case _ =>
          }

          traverse(call.getEffectiveInvokedExpr)
          super.visitMethodCallExpression(call)
        }
      })
    }

    traverse(patternDef)

    res
  }
}
