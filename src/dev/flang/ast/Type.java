/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa GmbH, Berlin
 *
 * Source of class Type
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Type <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Type extends ANY implements Comparable
{

  //  static int counter;  {counter++; if ((counter&(counter-1))==0) { System.out.println("######################"+counter+" "+this.getClass()); if(false)Thread.dumpStack(); } }

  /*----------------------------  constants  ----------------------------*/


  /**
   * Pre-allocated empty type list. NOTE: There is a specific empty type List
   * Call.NO_GENERICS which is used to distinguish "a.b<>()" (using Type.NONE)
   * from "a.b()" (using Call.NO_GENERICS).
   */
  public static final List<Type> NONE = new List<Type>();


  /**
   * pre-allocated empty array of types
   */
  static final Type[] NO_TYPES = new Type[0];


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this type, used for error messages.
   */
  public final SourcePosition pos;


  /**
   *
   */
  boolean ref;


  /**
   *
   */
  public final String name;


  /**
   *
   */
  public final List<Type> _generics;


  /**
   * The outer type, for the type p.q.r in the code
   *
   * a
   * {
   *   b
   *   {
   *     c
   *     {
   *       x p.q.r;
   *     }
   *   }
   *   p { ... }
   * }
   *
   * the outer_ of "r" is "p.q", and the outer of "q" is "p".
   *
   * However, if p is declared in a, after type resolution, the outer type of
   * "p" is "a" or maybe a heir of "a".
   */
  Type outer_;


  /**
   * Flag that indicates the end of the outer_ chain as it came from the source
   * code, even though after type resolution, outer_ might be non-null and point
   * to the actual outer_ type.
   */
  boolean outerMostInSource_ = false;


  /**
   *
   */
  Feature feature;


  /**
   * In case this is the name of a generic argument in an outer feature, this
   * will be set to that generic argument during findGenerics.  Knowing the
   * generics is a pre-requisite to resolving types.
   */
  Generic generic;


  /**
   * For debugging only: Make sure the findGenerics was called before
   * replaceGeneric or resolve is called.
   */
  boolean checkedForGeneric = false;

  enum YesNo
  {
    yes,
    no,
    dontKnow
  }

  /**
   * Cached result of dependsOnGenerics().
   */
  YesNo dependsOnGenerics = YesNo.dontKnow;


  /**
   * Cached result of calling Types.intern(this).
   */
  Type _interned = null;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param n
   *
   * @param g
   *
   * @param o
   */
  public Type(SourcePosition pos, String n, List<Type> g, Type o)
  {
    this(pos, n,g,o,null, false);
  }


  /**
   * Constructor to create a type from an existing type after formal generics
   * have been replaced in the generics arguments and in the outer type.
   *
   * @param t the original type
   *
   * @param g the actual generic arguments that replace t.generics
   *
   * @param o the actual outer type, or null, that replaces t.outer
   */
  public Type(Type t, List<Type> g, Type o)
  {
    this(t.pos, t.name, g, o, t.feature, t.ref);

    if (PRECONDITIONS) require
      ( (t._generics instanceof FormalGenerics.AsActuals) || t._generics.size() == g.size(),
       !(t._generics instanceof FormalGenerics.AsActuals) || ((FormalGenerics.AsActuals)t._generics).sizeMatches(g),
        (t.outer() == null) == (o == null));

    outerMostInSource_ = t.outerMostInSource();
    checkedForGeneric = t.checkedForGeneric;
  }


  /**
   * Constructor
   *
   * @param n
   *
   * @param g the actual generic arguments
   *
   * @param o
   *
   * @param f if this type corresponds to a feature, then this is the
   * feature, else null.
   *
   * @param ref true iff this type should be a ref type, otherwise it will be a
   * value type.
   */
  public Type(SourcePosition pos, String n, List<Type> g, Type o, Feature f, boolean ref)
  {
    if (PRECONDITIONS) require
      (pos != null,
       n.length() > 0);

    this.pos = pos;
    this.name  = n;
    this._generics = ((g == null) || g.isEmpty()) ? NONE : g;
    this.outer_ = o;
    this.feature = f;
    this.generic = null;
    this.ref = ref;
    this.checkedForGeneric = f != null;
  }


  /**
   * Constructor for a direct use of a generic argument. For a feature
   *
   *   feat<A,B,c>
   *   {
   *     inner { ...outer... }
   *   }
   *
   * the type of the outer reference within inner is feat<A, B, C>.  This
   * constructor is used to create A, B, C in this case.
   *
   * @param g the formal generic this referes to
   */
  public Type(SourcePosition pos, Generic g)
  {
    if (PRECONDITIONS) require
      (g != null);

    this.pos = pos;
    this.name  = g._name;
    this._generics = NONE;
    this.outer_ = null;
    this.feature = null;
    this.generic = g;
    this.ref = false;
    this.checkedForGeneric = true;
  }


  /**
   * Constructor for built-in types
   *
   * @param n the name, such as "int", "bool".
   */
  public Type(String n)
  {
    this(false, n);
  }


  /**
   * Constructor for built-in types
   *
   * @param ref true iff we create a ref type
   *
   * @param n the name, such as "int", "bool".
   */
  private Type(boolean ref, String n)
  {
    if (PRECONDITIONS) require
      (n.length() > 0);

    this.pos               = SourcePosition.builtIn;
    this.name              = n;
    this._generics          = NONE;
    this.outer_            = null;
    this.feature           = null;
    this.ref               = ref;
    this.checkedForGeneric = true;
  }

  /**
   * Constructor for built-in types
   *
   * @param n the name, such as "int", "bool".
   */
  public static Type type(String n, Feature universe)
  {
    if (PRECONDITIONS) require
      (n.length() > 0);

    return type(false, n, universe);
  }

  /**
   * Constructor for built-in types
   *
   * @param ref true iff we create a ref type
   *
   * @param n the name, such as "int", "bool".
   */
  public static Type type(boolean ref, String n, Feature universe)
  {
    if (PRECONDITIONS) require
      (n.length() > 0);

    return new Type(ref, n).resolve(universe);
  }


  /**
   * Create a ref type from a given value type.
   *
   * @param original the original value type
   *
   * @param isRef must be true.
   */
  public Type(Type original, boolean isRef)
  {
    if (PRECONDITIONS) require
      (!original.isRef(),
       isRef);

    this.pos                = original.pos;
    this.ref                = isRef;
    this.name               = original.name;
    this._generics           = original._generics;
    this.outer_             = original.outer_;
    this.outerMostInSource_ = original.outerMostInSource_;
    this.feature            = original.feature;
    this.generic            = original.generic;
    this.checkedForGeneric  = original.checkedForGeneric;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Call Constructor for a function type that returns a result
   *
   * @param returnType the result type.
   *
   * @param arguments the arguments list
   *
   * @return a Type instance that represents this function
   */
  public static Type funType(SourcePosition pos, Type returnType, List<Type> arguments)
  {
    if (PRECONDITIONS) require
      (returnType != null,
       arguments != null);

    // This is called during parsing, so Types.resolved.f_function is not set yet.
    return new Type(pos,
                    Types.FUNCTION_NAME,
                    new List<Type>(returnType, arguments),
                    null);
  }


  /**
   * call Constructor for a function type that defines a routine that returns nothing
   *
   * @param arguments the arguments list
   *
   * @return a Type instance that represents this function
   */
  public static Type funType(SourcePosition pos, List<Type> arguments)
  {
    if (PRECONDITIONS) require
      (arguments != null);

    // This is called during parsing, so Types.resolved.f_routine is not set yet.
    return new Type(pos,
                    Types.ROUTINE_NAME,
                    arguments,
                    null);
  }


  /**
   * setRef is called by the parser when parsing a type of the form "ref
   * <simpletype>".
   */
  public void setRef()
  {
    if (PRECONDITIONS) require
      (!this.ref);

    this.ref = true;
  }


  /**
   * isRef
   */
  public boolean isRef()
  {
    return this.ref || ((feature != null) && feature.isThisRef());
  }

  /**
   * setOuter
   *
   * @param t
   */
  public void setOuter(Type t)
  {
    if (this.outer_ == null)
      {
        check(_interned == null);
        this.outer_ = t;
      }
    else
      {
        this.outer_.setOuter(t);
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    String result;

    if (Types.INTERNAL_NAMES.contains(name))
      {
        return name;
      }
    else if (generic != null)
      {
        result = generic.feature().qualifiedName() + "." + name;
      }
    else if (outer_ != null)
      {
        String outer = outer_.toString();
        result = ""
          + (outer == "" ||
             outer == Feature.UNIVERSE_NAME ? ""
                                            : outer + ".")
          + (isRef() && (feature == null || !feature.isThisRef()) ? "ref "
                                                                  : "" )
          + (feature == null ? name
                             : feature._featureName.baseName());
      }
    else if (feature == null)
      {
        result = name;
      }
    else
      {
        result = feature.qualifiedName();
      }
    if (_generics != NONE)
      {
        result = result + "<" + _generics + ">";
      }
    return result;
  }


  /**
   * Replace formal generics from this type's feature in given list by the
   * actual generic arguments of this type.
   *
   * @param genericsToReplace a list of possibly generic types
   *
   * @return a new list of types with all formal generic arguments from
   * featureOfType() replaced by the corresponding generics entry of this type.
   */
  public List<Type> replaceGenerics(List<Type> genericsToReplace)
  {
    if (PRECONDITIONS) require
      (featureOfType().generics.sizeMatches(_generics));

    return actualTypes(featureOfType(), genericsToReplace, _generics);
  }


  /**
   * Does this type (or its outer type) depend on generics. If not, actualType()
   * will not need to do anything on this.
   */
  private boolean dependsOnGenerics()
  {
    YesNo result = dependsOnGenerics;

    if (PRECONDITIONS) require
      (checkedForGeneric);

    if (result == YesNo.dontKnow)
      {
        if (isGenericArgument())
          {
            result = YesNo.yes;
          }
        else if (_generics != NONE)
          {
            result = YesNo.yes;
          }
        else if (outer() != null)
          {
            result = outer().dependsOnGenerics() ? YesNo.yes : YesNo.no;
          }
        else
          {
            result = YesNo.no;
          }
        dependsOnGenerics = result;
      }
    return dependsOnGenerics == YesNo.yes;
  }


  /**
   * Create a new type with the outer type at level d (counted from the
   * innermost type) replaced by target.
   */
  public Type rebase(Type target, int d)
  {
    var result = this;
    if (d == 0)
      {
        result = target;
      }
    else
      {
        var o = outer();
        var r = o.rebase(target, d-1);
        if (r != o)
          {
            result = Types.intern(new Type(this, _generics, r));
          }
      }
    return result;
  }


  /**
   * Replace generic types used in given type t by the actual generic arguments
   * given in this.
   *
   * @param t a possibly generic type, must not be an open generic.
   *
   * @return t with all generic arguments from this.featureOfType._generics
   * replaced by this._generics.
   */
  public Type actualType(Type t)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric,
       t.checkedForGeneric,
       Errors.count() > 0 || !t.isOpenGeneric(),
       featureOfType().generics.sizeMatches(_generics));

    Type result = t;
    if (t.dependsOnGenerics())
      {
        result = t.actualType(featureOfType(), _generics);
        if (outer() != null)
          {
            result = outer().actualType(result);
          }
      }
    return result;
  }


  /**
   * Check if type t depends on a formal generic parameter of this. If so,
   * replace t by the corresponding actual generic parameter from the list
   * provided.
   *
   * @param f the feature actualGenerics belong to.
   *
   * @param actualGenerics the actual generic parameters
   *
   * @return t iff t does not depend on a formal generic parameter of this,
   * otherwise the type that results by replacing all formal generic parameters
   * of this in t by the corresponding type from actualGenerics.
   */
  public Type actualType(Feature f, List<Type> actualGenerics)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric,
       Errors.count() > 0 ||
       f.generics.sizeMatches(actualGenerics),
       Errors.count() > 0 || !isOpenGeneric() || genericArgument().formalGenerics() != f.generics);

    Type result = this;
    if (f != null)
      {
        for (Call i : f.inherits)
          {
            result = result.actualType(i.calledFeature(),
                                       i.generics);
          }
      }
    if (result.isGenericArgument())
      {
        Generic g = result.genericArgument();
        if (f != null && g.formalGenerics() == f.generics)  // t is replaced by corresponding actualGenerics entry
          {
            result = result.ensureNotOpen() ? g.replace(actualGenerics)
                                            : Types.t_ERROR;
          }
      }
    else
      {
        List<Type> g2 = actualTypes(f, result._generics, actualGenerics);
        Type o2 = (result.outer() == null) ? null : result.outer().actualType(f, actualGenerics);
        if (g2 != result._generics ||
            o2 != result.outer()    )
          {
            result = new Type(result, g2, o2);
          }
      }
    return result;
  }



  /**
   * Replace generic types used in given List of types by the actual generic arguments
   * given as actualGenerics.
   *
   * @param f the feature the generics belong to.
   *
   * @param genericsToReplace a list of possibly generic types
   *
   * @param actualGenerics the actual generics to feat that shold replace the
   * formal generics found in genericsToReplace.
   *
   * @return a new list of types with all formal generic arguments from this
   * replaced by the corresponding actualGenerics entry.
   */
  private static List<Type> actualTypes(Feature f, List<Type> genericsToReplace, List<Type> actualGenerics)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 ||
       f.generics.sizeMatches(actualGenerics));

    List<Type> result = genericsToReplace;
    if (f != null && !genericsToReplace.isEmpty())
      {
        if (genericsToReplace == f.generics.asActuals())  /* shortcut for properly handling open generics list */
          {
            result = actualGenerics;
          }
        else
          {
            boolean changes = false;
            for (Type t: genericsToReplace)
              {
                changes = changes || t.actualType(f, actualGenerics) != t;
              }
            if (changes)
              {
                result = new List<Type>();
                for (Type t: genericsToReplace)
                  {
                    result.add(t.actualType(f, actualGenerics));
                  }
              }
          }
      }
    return result;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public Type visit(FeatureVisitor v, Feature outerfeat)
  {
    if ((feature == null) && (generic == null))
      {
        if (outer_ != null)
          {
            Type t = outer_.visit(v, outerfeat);
            if (t != outer_)
              {
                check(_interned == null);
                outer_ = outer_.visit(v, outerfeat);
              }
          }
      }
    if (!_generics.isEmpty() && !(_generics instanceof FormalGenerics.AsActuals))
      {
        ListIterator<Type> i = _generics.listIterator();
        while (i.hasNext())
          {
            var gt = i.next();
            var ng = gt.visit(v, outerfeat);
            check
              (gt == ng || _interned == null);
            i.set(ng);
          }
      }
    return v.action(this, outerfeat);
  }


  /**
   * True iff this is not generic and neither its outer type nor actual generics
   * are generic up to the point this type is shown in the source (i.e., the
   * surrounding feature that contains this type declaration may be generic).
   */
  public boolean isFreeFromFormalGenericsInSource()
  {
    check
      (checkedForGeneric);

    boolean result =
      outerMostInSource() || outer_.isFreeFromFormalGenericsInSource();
    if (!this._generics.isEmpty())
      {
        for (Type t : this._generics)
          {
            result = result && t.isFreeFromFormalGenericsInSource();
          }
      }
    return result;
  }


  /**
   * True iff this is not generic nad neither its outer type nor actual generics
   * are generic.
   *
   * This is valid only after type resolution since before that, the outer clazz
   * is set only up to the point it is known in the source code.
   */
  public boolean isFreeFromFormalGenerics()
  {
    check
      (checkedForGeneric,
       feature != null || generic != null);

    boolean result =
      generic == null &&
      (outer() == null || outer().isFreeFromFormalGenerics());

    if (!this._generics.isEmpty())
      {
        for (Type t : this._generics)
          {
            result = result && t.isFreeFromFormalGenerics();
          }
      }
    return result;
  }


  /**
   * Find all the types used in this that refer to formal generic arguments of
   * this or any of this' outer classes.
   *
   * @param feat the root feature that contains this type.
   */
  public void findGenerics(Feature outerfeat)
  {
    //    if (PRECONDITIONS) require
    //      (!outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    if ((feature == null) && (generic == null))
      {
        if (outer() != null)
          {
            if (outer().isGenericArgument())
              {
                Errors.error(outer().pos,
                             "Formal generic cannot be used as outer type",
                             "In a type >>a.b<<, the outer type >>a<< must not be a formal generic argument.\n" +
                             "Type used: " + this + "\n" +
                             "Formal generic used " + outer() + "\n" +
                             "Formal generic declared in " + outer().genericArgument()._pos.show() + "\n");
              }
          }
        else
          {
            Feature o = outerfeat;
            do
              {
                generic = o.getGeneric(name);
                o = o.outer();
              }
            while (generic == null && o != null);

            if ((generic != null) && !_generics.isEmpty())
              {
                Errors.error(pos,
                             "Formal generic cannot have generic arguments",
                             "In a type with generic arguments >>A<B><<, the base type >>A<< must not be a formal generic argument.\n" +
                             "Type used: " + this + "\n" +
                             "Formal generic used " + generic + "\n" +
                             "Formal generic declared in " + generic._pos.show() + "\n");
              }
          }
      }

    checkedForGeneric = true;

    if (POSTCONDITIONS) ensure
      (checkedForGeneric);
  }


  /**
   * resolve artificial types t_VOID, t_ERROR, etc.
   */
  public void resolveArtificialType(Feature feat)
  {
    if (PRECONDITIONS) require
      (feature == null,
       Types.INTERNAL_NAMES.contains(name));

    feature = feat;

    Type interned = Types.intern(this);

    check
      (interned == this);
  }


  /**
   * resolve this type
   *
   * @param feat the outer feature that this type is declared in, used
   * for resolution of generic parameters etc.
   */
  public Type resolve(Feature outerfeat)
  {
    if (PRECONDITIONS) require
      (outerfeat != null,
       outerfeat.state().atLeast(Feature.State.RESOLVED_DECLARATIONS),
       checkedForGeneric);

    if (!outerfeat.isLastArgType(this))
      {
        ensureNotOpen();
      }
    if (!isGenericArgument())
      {
        Feature o = outerfeat;
        if (outer_ != null)
          {
            outer_ = outer_.resolve(outerfeat);
            if (outer_.isGenericArgument())
              { // an error message was generated already during findGenerics()
                check
                  (Errors.count() > 0);
                return Types.t_ERROR;
              }
            o = outer_.featureOfType();
          }
        if (feature == null)
          {
            var type_fs = new List<Feature>();
            var nontype_fs = new List<Feature>();
            do
              {
                var fs = o.findDeclaredOrInheritedFeatures(name).values();
                for (var f : fs)
                  {
                    if (f.returnType.isConstructorType())
                      {
                        type_fs.add(f);
                        feature = f;
                      }
                    else
                      {
                        nontype_fs.add(f);
                      }
                  }
                if (type_fs.size() > 1)
                  {
                    FeErrors.ambiguousType(this, type_fs);
                    return Types.t_ERROR;
                  }
                o = o.outer();
              }
            while (feature == null && o != null);

            if (feature == null)
              {
                FeErrors.typeNotFound(this, outerfeat, nontype_fs);
                return Types.t_ERROR;
              }
          }
        FormalGenerics.resolve(_generics, outerfeat);
        if (!feature.generics.errorIfSizeOrTypeDoesNotMatch(_generics,
                                                            pos,
                                                            "type",
                                                            "Type: " + toString() + "\n"))
          {
            return Types.t_ERROR;
          }
      }
    return Types.intern(this);
  }


  /**
   * Check that in case this is a choice type, it is valid, i.e., it is a value
   * type and the generic arguments to the choice are different.  Create compile
   * time errore in case this is not the case.
   */
  void checkChoice(SourcePosition pos)
  {
    if (feature != null)
      {
        List<Type> g = feature.choiceGenerics();
        if (g != null)
          {
            g = replaceGenerics(g);
            if (isRef())
              {
                Errors.error(pos,
                             "ref to choice type is not allowed",
                             "a choice is always a value type");
              }

            int i1 = 0;
            for (Type t1 : g)
              {
                t1 = Types.intern(t1);
                int i2 = 0;
                for (Type t2 : g)
                  {
                    t2 = Types.intern(t2);
                    if (i1 < i2)
                      {
                        if (t1 == t2 ||
                            !t1.isGenericArgument() &&
                            !t2.isGenericArgument() &&
                            (t1.isAssignableFrom(t2) ||
                             t2.isAssignableFrom(t1)    ))
                          {
                            Errors.error(pos,
                                         "Generics arguments to choice type must be disjoint types",
                                         "The following types have overlapping values:\n" +
                                         t1 + /* " at " + t1.pos.show() + */ "\n" +  // NYI: use pos before Types were interned!
                                         t2 + /* " at " + t2.pos.show() + */ "\n");
                          }
                      }
                    i2++;
                  }
                i1++;
              }
          }
      }
  }


  /**
   * isGenericArgument
   *
   * @return
   */
  public boolean isGenericArgument()
  {
    if (PRECONDITIONS) require
      (checkedForGeneric);

    return generic != null;
  }


  /**
   * featureOfType
   *
   * @return
   */
  public Feature featureOfType()
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || !isGenericArgument());

    Feature result = feature;

    if (result == null)
      {
        check
          (Errors.count() > 0);

        result = Types.f_ERROR;
      }

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * genericArgument
   *
   * @return
   */
  public Generic genericArgument()
  {
    if (PRECONDITIONS) require
      (isGenericArgument());

    Generic result = generic;

    if (POSTCONDITIONS) ensure
      (result != null);

    return result;
  }


  /**
   * is this a formal generic argument that is open, i.e., the last argument in
   * a formal generic arguments list and followed by ... as A in
   * Funtion<R,A...>.
   *
   * This type needs very special treatment, it is allowed only as an argument
   * type of the last argument in an abstract feature declaration.  When
   * replacing generics by actual generics arguments, this gets replaced by a
   * (possibly empty) list of actual types.
   *
   * @return true iff this is an open generic
   */
  public boolean isOpenGeneric()
  {
    if (PRECONDITIONS) require
      (checkedForGeneric);

    return isGenericArgument() && genericArgument().isOpen();
  }


  /**
   * Check if this.isOpenGeneric(). If so, create a compile-time error.
   *
   * @return true iff !isOpenGeneric()
   */
  public boolean ensureNotOpen()
  {
    boolean result = true;

    if (PRECONDITIONS) require
      (checkedForGeneric);

    if (isOpenGeneric())
      {
        Errors.error(pos,
                     "Illegal use of open formal generic type",
                     "Open formal generic type is permitted only as the type of the last argument in a formal arguments list of an abstract feature.\n" +
                     "Open formal argument: " + generic);
        result = false;
      }
    return result;
  }


  /**
   * isFunType checks if this is a function type, e.g., "fun (int x,y) String".
   *
   * @return true iff this is a fun type
   */
  public boolean isFunType()
  {
    return
      feature == Types.resolved.f_function ||
      feature == Types.resolved.f_routine;
  }


  /**
   * Compare this to other for creating unique types.
   */
  public int compareTo(Object other)
  {
    return compareTo((Type) other);
  }


  /**
   * Compare this to other for creating unique types.
   */
  public int compareTo(Type other)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric,
       other != null,
       other.checkedForGeneric,
       getClass() == Type.class,
       other.getClass() == Type.class,
                     isGenericArgument() == (              feature == null),
       ((Type)other).isGenericArgument() == (((Type)other).feature == null));

    int result = compareToIgnoreOuter(other);
    if (result == 0 && generic == null)
      { // NYI: outerMostInSource is ignored for interned Types, maybe we should
        // create new types where outerMostInSource is always false?
        if (false && outerMostInSource() != other.outerMostInSource())
          {
            result = outerMostInSource() ? -1 : +1;
          }
        else
          {
            var to = this .outerInterned();
            var oo = other.outerInterned();
            result =
              (to == null && oo == null) ?  0 :
              (to == null && oo != null) ? -1 :
              (to != null && oo == null) ? +1 : to.compareTo(oo);
          }
      }
    return result;
  }


  /**
   * Compare this to other ignoring the outer type. This is used for created in
   * clazzes when the outer clazz is known.
   */
  public int compareToIgnoreOuter(Type other)
  {
    if (PRECONDITIONS) require
      (checkedForGeneric,
       other != null,
       other.checkedForGeneric,
       getClass() == Type.class,
       other.getClass() == Type.class,
                     isGenericArgument() == (              feature == null),
       ((Type)other).isGenericArgument() == (((Type)other).feature == null));

    int result = 0;

    if (this != other)
      {
        result =
          (feature == null) && (other.feature == null) ?  0 :
          (feature == null) && (other.feature != null) ? -1 :
          (feature != null) && (other.feature == null) ? +1 : feature.compareTo(other.feature);
        if (result == 0)
          {
            if (_generics.size() != other._generics.size())  // this may happen for open generics lists
              {
                result = _generics.size() < other._generics.size() ? -1 : +1;
              }
            else
              {
                Iterator<Type> tg = _generics.iterator();
                Iterator<Type> og = other._generics.iterator();
                while (tg.hasNext() && result == 0)
                  {
                    var tgt = Types.intern(tg.next());
                    var ogt = Types.intern(og.next());
                    result = tgt.compareTo(ogt);
                  }
              }
          }
        if (result == 0)
          {
            check
              (feature == null ||
               name.equals(feature._featureName.baseName()) ||
               Types.INTERNAL_NAMES.contains(name),
               generic == null || name.equals(generic._name),
               (feature == null) ^ (generic == null) || (Errors.count() > 0));

            result = name.compareTo(other.name);
          }
        if (result == 0)
          {
            if (isRef() ^ other.isRef())
              {
                result = isRef() ? -1 : 1;
              }
          }
        if (result == 0)
          {
            // NYI: generics should not be stored globally, but locally to generic.feature
            result =
              (generic == null) && (other.generic == null) ?  0 :
              (generic == null) && (other.generic != null) ? -1 :
              (generic != null) && (other.generic == null) ? +1 : generic.feature().compareTo(other.generic.feature());

            if (result == 0)
              {
                if (generic != null)
                  {
                    result = generic._name.compareTo(other.generic._name); // NYI: compare generic, not generic.name!
                  }
              }
          }
      }
    return result;
  }


  /**
   * Is this type the outermost part of a type declared in source code?
   */
  public boolean outerMostInSource()
  {
    return outerMostInSource_ || outer_ == null;
  }


  /**
   * Get an interned version of outer() or null if none.
   */
  Type outerInterned()
  {
    Type result = outer();
    if (result != null)
      {
        result = Types.intern(result);
      }
    return result;
  }

  /**
   * outer type, after type resolution. This provides the whole chain of types
   * until Types.resolved.universe.thisType(), while the outer_ field ends with
   * the outermost type explicitly written in the source code.
   */
  public Type outer()
  {
    Type result = outer_;
    if (result == null)
      {
        if (feature != null && feature.state().atLeast(Feature.State.LOADED))
          {
            Feature of = feature.outer();
            if (of == null)
              {
                return null;
              }
            else
              {
                return of.thisType();
              }
          }
        else if (generic != null)
          {
            check(Errors.count() > 0);
          }
      }
    return result;
  }

  /**
   * Helper for isAssignableFrom: check if this is a choice type and actual is
   * assignable to one of the generic arguments to this choice.
   *
   * @return true iff this is a choice and actual is assignable to one of the
   * generic arguments of this choice.
   */
  private boolean isChoiceMatch(Type actual)
  {
    if (PRECONDITIONS) require
      (feature != null || Errors.count() > 0);

    boolean result = false;
    if (feature != null && !isRef())
      {
        List<Type> g = feature.choiceGenerics();
        if (g != null)
          {
            for (Type t : actualTypes(feature, g, _generics))
              {
                if (Types.intern(t).isAssignableFrom(actual))
                  {
                    result = true;
                  }
              }
          }
      }
    return result;
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * @param actual the actual type.
   */
  public boolean isAssignableFrom(Type actual)
  {
    return isAssignableFrom(actual, null);
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * @param actual the actual type.
   *
   * @param assignableTo in case we want to show all types actual is assignable
   * to in an error message, this collects the types.
   */
  boolean isAssignableFrom(Type actual, List<Type> assignableTo)
  {
    if (PRECONDITIONS) require
      (Types.intern(this  ) == this,
       Types.intern(actual) == actual,
       this  .feature != null || this  .isGenericArgument() || Errors.count() > 0,
       actual.feature != null || actual.isGenericArgument() || Errors.count() > 0,
       Errors.count() > 0 || this != Types.t_ERROR && actual != Types.t_ERROR);

    boolean result;
    if (assignableTo != null)
      {
        assignableTo.add(actual);
      }
    if (this == actual
        || ((this   == Types.t_VOID || this   == Types.resolved.t_void) &&
            (actual == Types.t_VOID || actual == Types.resolved.t_void)    )
        || this   == Types.t_ERROR
        || actual == Types.t_ERROR)
      {
        result = true;
      }
    else if (actual.isGenericArgument())
      {
        result = isAssignableFrom(actual.generic.constraint(), assignableTo);
      }
    else if (isGenericArgument())
      {
        result = generic.constraint().isAssignableFrom(actual, assignableTo);
      }
    else
      {
        result = false;
        if (isRef() /* && actual.isRef() -- NYI: allow autoboxing! */)
          {
            check
              (actual.feature != null || Errors.count() > 0);
            if (actual.feature != null)
              {
                for (Call p: actual.feature.inherits)
                  {
                    Type pt = Types.intern(actual.actualType(p.type()));
                    if (actual.isRef() && !pt.isRef())
                      {
                        pt = Types.intern(new Type(pt, true));
                      }
                    if (isAssignableFrom(pt, assignableTo))
                      {
                        result = true;
                      }
                  }
              }
          }
        if (!result)
          {
            result = isChoiceMatch(actual);
          }
      }
    return result;
  }


  /**
   * Check if this or any of its generic arguments is Types.t_ERROR.
   */
  private boolean containsError()
  {
    boolean result = false;
    if (this == Types.t_ERROR)
      {
        result = true;
      }
    else if (!_generics.isEmpty())
      {
        for (Type t: _generics)
          {
            result = result || t.containsError();
          }
      }
    return result;
  }


  /**
   * Check if a value of static type actual can be assigned to a field of static
   * type this.  This performs static type checking, i.e., the types may still
   * be or depend on generic parameters.
   *
   * In case any of the types involved are or contain t_ERROR, this returns
   * true. This is convenient to avoid the creation of follow-up errors in this
   * case.
   *
   * @param actual the actual type.
   */
  public boolean isAssignableFromOrContainsError(Type actual)
  {
    return
      containsError() || actual.containsError() || isAssignableFrom(actual);
  }


  /**
   * Find a type that is assignable from values of two types, this and t. If no
   * such type exists, return Types.resovled.t_VOID.
   *
   * @param that another type or null
   *
   * @return a type that is assignable both from this and that, or null if none
   * exists.
   */
  Type union(Type that)
  {
    Type result =
      this == Types.t_ERROR       ? Types.t_ERROR     :
      that == Types.t_ERROR       ? Types.t_ERROR     :
      this == Types.t_UNDEFINED   ? Types.t_UNDEFINED :
      that == Types.t_UNDEFINED   ? Types.t_UNDEFINED :
      this == Types.t_ANY         ? that              :
      that == Types.t_ANY         ? this              :
      this.isAssignableFrom(that) ? this :
      that.isAssignableFrom(this) ? that : Types.t_UNDEFINED;

    if (POSTCONDITIONS) ensure
      (result == Types.t_UNDEFINED ||
       result == Types.t_ERROR     ||
       this == Types.t_ANY && result == that ||
       that == Types.t_ANY && result == this ||
       result.isAssignableFrom(this) && result.isAssignableFrom(that));

    return result;
  }

}

/* end of file */
