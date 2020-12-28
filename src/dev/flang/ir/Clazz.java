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
 * Source of class Clazz
 *
 *---------------------------------------------------------------------*/

package dev.flang.ir;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.Assign; // NYI: remove dependency!
import dev.flang.ast.Box; // NYI: remove dependency!
import dev.flang.ast.Call; // NYI: remove dependency!
import dev.flang.ast.Case; // NYI: remove dependency!
import dev.flang.ast.Consts; // NYI: remove dependency!
import dev.flang.ast.Expr; // NYI: remove dependency!
import dev.flang.ast.FeErrors; // NYI: remove dependency!
import dev.flang.ast.Feature; // NYI: remove dependency!
import dev.flang.ast.FeatureVisitor; // NYI: remove dependency!
import dev.flang.ast.Impl; // NYI: remove dependency!
import dev.flang.ast.Match; // NYI: remove dependency!
import dev.flang.ast.SingleType; // NYI: remove dependency!
import dev.flang.ast.Type; // NYI: remove dependency!
import dev.flang.ast.Types; // NYI: remove dependency!

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Clazz represents a runtime type, i.e, a Type with actual generic arguments.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Clazz extends ANY implements Comparable
{

  //  static int counter;  {counter++; if ((counter&(counter-1))==0) System.out.println("######################"+counter+" "+this.getClass()); }
  // { if ((counter&(counter-1))==0) Thread.dumpStack(); }


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public final Type type;


  /**
   *
   */
  public final Clazz outer;


  /**
   *
   */
  int size = Integer.MIN_VALUE;


  public final Map<Feature, Clazz> clazzForField_ = new TreeMap<>();

  public final Map<Feature, Integer> offsetForField_ = new TreeMap<>();


  /**
   * Clazzes required during runtime. These are indexed by
   * Feature.getRuntimeClazzId and used to quickly find the actual class
   * depending on the actual generic parameters given in this class or its super
   * classes.
   */
  ArrayList<Object> runtimeClazzes = new ArrayList<>();


  /**
   * Cached result of choiceGenerics(), only used if isChoice() and
   * !isChoiceOfOnlyRefs().
   */
  public ArrayList<Clazz> choiceGenerics_;


  /**
   * Offset ańd size of choice values stored in this.
   */
  public int choiceValsOffset_ = -1;
  public int choiceValsSize_ = -1;


  /**
   * Flag that is set while the layout of objects of this clazz is determined.
   * This is used to detect recursive clazzes that contain value type fields of
   * the same type as the clazz itself.
   */
  boolean layouting_ = false;


  /**
   * Will instances of this class be created?
   */
  public boolean isInstantiated_ = false;


  /**
   * If instances of this class are created, this gives a source code position
   * that does create such an instance.  To be used in error messages.
   */
  SourcePosition instantiationPos_ = null;


  /**
   * In case abstract methods are called on this, this lists the abstract
   * methods that have been found to do so.
   */
  TreeSet<Feature> abstractCalled_ = null;


  /**
   * NYI: under developemnt: An attempt to track the 'real' outer clazz, i.e.,
   * the outer clazz as seen through the inheritance chain.  Does not work well
   * yet, see Hellq.fu:

Hellq is
  a is
    b is
      c is
        p is stdout.println("a.b.c.p!")
        d is
          e is
            stdout.println("e");
            p

  stdout.println("Hellq");

  q : a.b.c.d is
    stdout.println("q");

  stdout.println("q.e:");
  q.e
  stdout.println("q.e!");

   * NYI: Remove, this is a hack, it is not clear what a "real" outer reference
   * should be.  Instead, there are many outer refs, one for every inheritance
   * call, and additional ones that have been inherited.
   */
  final Clazz[] _realOuter;


  /**
   * The dynamic binding implementation used for this clazz. null if !isRef().
   */
  public DynamicBinding _dynamicBinding;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param type
   *
   * @param outer
   */
  public Clazz(Type actualType, Clazz outer)
  {
    if (PRECONDITIONS) require
      (!Clazzes.closed,
       Errors.count > 0 || !actualType.isGenericArgument(),
       Errors.count > 0 || actualType.isFreeFromFormalGenerics(),
       actualType.featureOfType().outer() == null || outer.feature().inheritsFrom(actualType.featureOfType().outer()),
       actualType == Types.t_ERROR ||
       actualType == Types.t_VOID  || actualType.featureOfType().outer() != null || outer == null);

    this.type = actualType;
    this.outer = outer;
    var d = feature().depth();
    this._realOuter = new Clazz[d+1];
    for (int i = 0; i <= d; i++)
      {
        this._realOuter[i] = outer != null && i < outer._realOuter.length ? outer._realOuter[i] : this;
      }

    if (isChoice())
      {
        choiceGenerics_ = choiceGenerics();
      }
    this._dynamicBinding = null;
  }


  /**
   * NYI: Remove, this is a hack, it is not clear what a "real" outer reference
   * should be.  Instead, there are many outer refs, one for every inheritance
   * call, and additional ones that have been inherited.
   */
  int realDepth()
  {
    return _realOuter.length;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * size
   *
   * @param u
   *
   * @return
   */
  public int size()
  {
    if (PRECONDITIONS) require
      (size >= 0,
       !layouting_);

    return size;
  }


  /**
   * Convert a given type to the actual type within this class. An
   * actual type does not refer to any formal generic arguments.
   */
  public Type actualType(Type t)
  {
    if (PRECONDITIONS) require
      (Errors.count > 0 || !t.isOpenGeneric());

    t = type.actualType(t);
    if (outer != null)
      {
        t = outer.actualType(t);
      }
    return Types.intern(t);
  }


  /**
   * Convert a given type to the actual runtime clazz within this class. The
   * formal generics arguments will first be replaced via actualType(t), and the
   * Clazz will be created from the result.
   */
  public Clazz actualClazz(Type t)
  {
    if (PRECONDITIONS) require
      (Errors.count > 0 || !t.isOpenGeneric());

    return Clazzes.clazz(actualType(t));
  }


  /**
   * Convert the given generics to the actual generics of this class.
   *
   * @param generics a list of generic arguments that might itself consist of
   * formal generics
   *
   * @return The list of actual generics after replacing the generics of this
   * class or its outer classes.
   */
  public List<Type> actualGenerics(List<Type> generics)
  {
    generics = type.replaceGenerics(generics);
    if (outer != null)
      {
        generics = outer.actualGenerics(generics);
      }
    return generics;
  }


  /**
   * The feature underlying this clazz.
   */
  public Feature feature()
  {
    return type.featureOfType();
  }


  /**
   * isRef
   */
  public boolean isRef()
  {
    return type.isRef();
  }


  /**
   * Does this clazz have a state, i.e., does it have any runtime fields that
   * could hold values that distinguish two instances of this clazz?
   *
   * @return true iff instances of this clazz have state.
   */
  private boolean hasState()
  {
    boolean result = false;

    for (Feature f: feature().allInnerAndInheritedFeatures())
      {
        if (f.isField() && Clazzes.isUsed(f, this))
          {
            result = true;
          }
      }

    return result;
  }


  /**
   * Do we need f (or its redefinition) to be present in this clazz' dynamic binding data?
   */
  boolean isAddedToDynamicBinding(Feature f)
  {
    return
      isRef() &&
      Clazzes.isUsed(f, this) &&
      (f.isField() ||
       f.isSingleton() ||
       f.isCalledDynamically())
      ;
  }


  /**
   * Layout this clazz. In case a cyclic nesting of value fields is detected,
   * report an error.
   */
  void layoutAndHandleCycle()
  {
    List<SourcePosition> cycle = layout();
    if (cycle != null && Errors.count <= IrErrors.count)
      {
        StringBuilder cycleString = new StringBuilder();
        for (SourcePosition p : cycle)
          {
            cycleString.append(p.show()).append("\n");
          }
        IrErrors.error(type.pos,
                       "Cyclic field nesting is not permitted",
                       "Cyclic value field nesting would result in infinitely large objects.\n" +
                       "Cycle of nesting found during clazz layout:\n" +
                       cycleString + "\n" +
                       "To solve this, you could change one or several of the fields involved to a reference type by adding 'ref' before the type.");
      }

    createDynamicBinding();
  }


  /**
   * layout this clazz.
   */
  private List<SourcePosition> layout()
  {
    List<SourcePosition> result = null;
    if (layouting_)
      {
        result = new List<>(type.pos);
      }
    else if (size == Integer.MIN_VALUE)
      {
        layouting_ = true;
        size = 0;
        if (isChoice())
          {
            int maxSz = 0;
            for (Clazz c : choiceGenerics())
              {
                if (result == null)
                  {
                    int sz;
                    if (c.isRef())
                      {
                        sz = 1;
                      }
                    else
                      {
                        result = c.layout();
                        sz = result == null ? c.size() : 0;
                      }
                    maxSz = sz > maxSz ? sz : maxSz;
                  }
              }
            choiceValsOffset_ = size;
            choiceValsSize_ = maxSz;
            size += maxSz;
          }

        for (var f: feature().allInnerAndInheritedFeatures())
          {
            if (result == null &&
                (f.isField() || f.isSingleton()) &&
                Clazzes.isUsed(f, this))
              {
                result = placeUsedField(f);
              }
          }

        layouting_ = false;
      }
    if (result != null)
      {
        result.add(type.pos);
      }
    return result;
  }


  /**
   * placeUsedFeature assigns a location within intances of this clazz to the
   * given field f.
   *
   * @param f a field
   *
   * @return a list of source positions in case of cyclic value fields
   */
  private List<SourcePosition> placeUsedField(Feature f)
  {
    if (PRECONDITIONS) require
      (f != null,
       f.isField() || f.isSingleton(),
       layouting_);

    List<SourcePosition> result = null;

    if (!f.resultType().isOpenGeneric() &&
        f == findRedefinition(f)  // NYI: proper field redefinition handling missing, see tests/redef_args/*
        )
      {
        Clazz fieldClazz = actualClazz(f.resultType());
        clazzForField_.put(f, fieldClazz);
        offsetForField_.put(f, size);
        Type ft = fieldClazz.type;
        int fsz = 0;
        if        (ft.isRef() || f.isSingleton()) { fsz = 1;
        } else if (ft == Types.resolved.t_i32   ) { fsz = 1;
        } else if (ft == Types.resolved.t_u32   ) { fsz = 1;
        } else if (ft == Types.resolved.t_i64   ) { fsz = 2;
        } else if (ft == Types.resolved.t_u64   ) { fsz = 2;
        } else {
          result = fieldClazz.layout();
          if (result != null)
            {
              result.add(f.pos);
            }
          else
            {
              fsz = fieldClazz.size();
            }
        }
        size = size + fsz;
      }
    return result;
  }


  /**
   * Create dynamic binding data for this clazz in case it is a ref.
   */
  private void createDynamicBinding()
  {
    if (isRef() && _dynamicBinding == null)
      {
        this._dynamicBinding = new DynamicBinding(this);

        // NYI: Inheritance must be done differently: We should
        // (recursively) traverse all parents and hand down features from
        // each parent to this clazz to find the actual FeatureName of the
        // feature after inheritance. For each parent for which the feature
        // is called, we need to set up a table in this tree that contains
        // the inherited feature or its redefinition.
        for (Feature f: feature().allInnerAndInheritedFeatures())
          {
            // if (isInstantiated_) -- NYI: if not instantiated, we do not need to add f to dynamic binding, but we seem to need its side-effects
            if (isAddedToDynamicBinding(f))
              {
                if ((f.isField() ||
                     f.isSingleton() /* NYI: ugly, try to avoid special handling for SingleType here */) &&
                    !f.resultType().isOpenGeneric()
                    )
                  {
                    var off = offsetForField_.get(f);
                    if (off != null) // NYI: Check this, why can the field offset be null?
                      {
                        _dynamicBinding.addFieldOffset(f, off);
                      }
                  }
                if (f.isCalledDynamically() && Clazzes.isCalledDynamically(f))
                  {
                    var innerClazz = lookup(f, Call.NO_GENERICS, f.isUsedAt());
                    var callable = Clazzes._backend_.callable(innerClazz, this);
                    _dynamicBinding.addCallable(f, callable);
                  }
              }
          }
      }
  }


  /**
   * find redefinition of a given feature in this clazz. NYI: This will have to
   * take the hole inheritance chain into account and the parent view that is
   * being filled with live into account:
   */
  private Feature findRedefinition(Feature f)
  {
    var fn = f.featureName();
    var tf = feature();
    if (tf != Types.f_ERROR && f != Types.f_ERROR)
      {
        var chain = tf.findInheritanceChain(f.outer());
        for (var p: chain)
          {
            fn = f.outer().handDown(f, fn, p, feature());  // NYI: need to update f/f.outer() to support several levels of inheritance correctly!
          }
      }
    return feature().findDeclaredOrInheritedFeature(fn);
  }


  /**
   * Lookup the code to call the feature f from this clazz using dynamic binding
   * if needed.
   *
   * This is not intended for use at runtime, but during analysis of static
   * types or to fill the virtual call table.
   */
  public Clazz lookup(Feature f, List<Type> actualGenerics, SourcePosition p)
  {
    Feature af = findRedefinition(f);

    check
      (Errors.count > 0 || af != null);

    Clazz innerClazz = null;
    if (af != null)
      {
        if (af.impl == Impl.ABSTRACT)
          {
            if (abstractCalled_ == null)
              {
                abstractCalled_ = new TreeSet<>();
              }
            abstractCalled_.add(af);
          }

        Type t = af.thisType().actualType(af, actualGenerics);
        t = actualType(t);
        innerClazz = Clazzes.clazzWithSpecificOuter(t, this);
        innerClazz.isInstantiated_ = true;
        innerClazz.instantiationPos_ = p;
        check
          (innerClazz.type.featureOfType() == af);
      }

    if (POSTCONDITIONS) ensure
      (af == null || innerClazz != null);

    return innerClazz;
  }


  /**
   * Get the runtime clazz of a field in this clazz.
   *
   * @param field a field
   */
  public Clazz clazzForField(Feature field)
  {
    check
      (field.isField() ||
       field.returnType == SingleType.INSTANCE /* NYI: ugly, try to avoid special handling for SingleType here */,
       feature().inheritsFrom(field.outer()));

    var result = clazzForField_.get(field);
    if (result == null)
      {
        result = actualClazz(field.resultType());
      }
    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    if (type == Types.t_VOID)
      {
        return "--VOID--";
      }
    else if (type == Types.t_UNDEFINED)
      {
        return "--UNDEFINED--";
      }
    else if (type == Types.t_ADDRESS)
      {
        return "--ADDRESS--";
      }
    else if (type == Types.t_ERROR)
      {
        return "--ERROR--";
      }
    else if (outer == null)
      {
        return type.toString();
      }
    else
      {
        return ""
          + ((outer == Clazzes.universe.get())
             ? ""
             : outer.toString() + ".")
          + (type.isRef()
             ? "ref "
             : ""
             )
          + feature().featureName().baseName() + (type._generics.isEmpty()
                              ? ""
                              : "<" + type._generics + ">");
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString2()
  {
    return "CLAZZ:" + type + (outer != null ? " in "+outer : "");
  }


  /**
   * Check if a value of clazz other can be assigned to a field of this clazz.
   *
   * @other the value to be assigned to a field of type this
   *
   * @return true iff other can be assigned to a field of type this.
   */
  public boolean isAssignableFrom(Clazz other)
  {
    return (this==other) || this.type.isAssignableFrom(other.type);
  }


  /**
   * Compare this to other for creating unique clazzes.
   */
  public int compareTo(Object other)
  {
    return compareTo((Clazz) other);
  }

  /**
   * Helper routine for compareTo: compare the outer classes.  If outer are refs
   * for both clazzes, they can be considered the same as long as their outer
   * classes (recursively) are the same. If they are values, they need to be
   * exactly equal.
   */
  private int compareOuter(Clazz other)
  {
    var to = outer;
    var oo = other.outer;
    return
      to == oo                   ? 0 :
      to == null                 ? -1 :
      oo == null                 ? +1 :
      to.isRef() && oo.isRef()   ? to.compareOuter(oo) :
      !to.isRef() && !oo.isRef() ? to.compareTo   (oo) :
      to.isRef()                 ? +1 : -1;
  }


  /**
   * Compare this to other for creating unique clazzes.
   */
  public int compareTo(Clazz other)
  {
    int result;

    if (PRECONDITIONS) require
      (other != null,
       this .getClass() == Clazz.class,
       other.getClass() == Clazz.class,
       this.type  == Types.intern(this.type),
       other.type == Types.intern(other.type));

    result = compareOuter(other);
    if (result == 0)
      {
        result = type.compareTo(other.type);
      }
    return result;
  }

  public boolean dependsOnGenerics()  //  NYI: Only used in caching for Type.clazz, which should be removed
  {
    return !type._generics.isEmpty() || (outer != null && outer.dependsOnGenerics());
  }


  /**
   * Visitor to find all runtime classes.
   */
  private class FindClassesVisitor extends FeatureVisitor
  {
    public void action     (Assign a, Feature outer) { Clazzes.findClazzes(a, Clazz.this); }
    public void action     (Box    b, Feature outer) { Clazzes.findClazzes(b, Clazz.this); }
    public void actionAfter(Case   c, Feature outer) { Clazzes.findClazzes(c, Clazz.this); }
    public Call action     (Call   c, Feature outer) { Clazzes.findClazzes(c, Clazz.this); return c; }
    public void action     (Match  m, Feature outer) { Clazzes.findClazzes(m, Clazz.this); }
    void visitAncestors(Feature f)
    {
      f.visit(this);
      for (Call c: f.inherits)
        {
          Feature cf = c.calledFeature();

          check
            (Errors.count > 0 || cf != null);

          if (cf != null)
            {
              visitAncestors(cf);
            }
        }
    }
  }


  /**
   * Find all clazzes referenced by this even if this is not executed.
   */
  void findAllClasses()
  {
  }


  /**
   * Find all inner clazzes of this that are referenced when this is executed
   */
  void findAllClassesWhenCalled()
  {
    var f = feature();
    new FindClassesVisitor().visitAncestors(f);
    for (Feature ff: feature().allInnerAndInheritedFeatures())
      {
        if (Clazzes.isUsed(ff, this))
          {
            Feature af = findRedefinition(ff);
            check
              (Errors.count > 0 || af != null);
            if (af != null &&
                (af.isField() ||
                 af.isSingleton() /* NYI: ugly, try to avoid special handling for SingleType here */)
                )
              {
                if (!af.resultType().isOpenGeneric()) // NYI: why?
                  {
                    Clazz fieldClazz = actualClazz(af.resultType());
                  }
              }
            if (isAddedToDynamicBinding(ff))
              {
                Clazzes.whenCalledDynamically(ff,
                                              () -> { var innerClazz = lookup(ff, Call.NO_GENERICS, ff.isUsedAt()); });
              }
          }
      }
  }


  /**
   * Find all clazzes that are created when f is called on this clazz.
   *
   * This determines all the possible runtime types of all calls within the code
   * of f and within the code of all clazzes f inherits from.
   *
   * @param f the feature that is called on this.
   */
  void findAllClasses(Expr target, Feature f)
  {
    target.visit(new FindClassesVisitor(), f);
  }


  /**
   * During findClazzes, store data for a given id.
   *
   * @param id the id obtained via Feature.getRuntimeClazzId()
   *
   * @param data the data to be stored for this id.
   */
  public void setRuntimeData(int id, Object data)
  {
    if (PRECONDITIONS) require
      (id >= 0,
       id < feature().runtimeClazzIdCount());

    int cnt = feature().runtimeClazzIdCount();
    runtimeClazzes.ensureCapacity(cnt);
    while (runtimeClazzes.size() < cnt)
      {
        runtimeClazzes.add(null);
      }
    runtimeClazzes.set(id, data);

    if (POSTCONDITIONS) ensure
      (getRuntimeData(id) == data);
  }


  /**
   * During execution, retrieve the data stored for given id.
   *
   * @param id the id used in setRuntimeData
   *
   * @return the data stored for this id.
   */
  public Object getRuntimeData(int id)
  {
    if (PRECONDITIONS) require
      (id < feature().runtimeClazzIdCount(),
       id >= 0);

    return runtimeClazzes.get(id);
  }


  /**
   * During findClazzes, store a clazz for a given id.
   *
   * @param id the id obtained via Feature.getRuntimeClazzId()
   *
   * @param cl the clazz to be stored for this id.
   */
  public void setRuntimeClazz(int id, Clazz cl)
  {
    if (PRECONDITIONS) require
      (id >= 0,
       id < feature().runtimeClazzIdCount());

    setRuntimeData(id, cl);

    if (POSTCONDITIONS) ensure
      (getRuntimeClazz(id) == cl);
  }


  /**
   * During execution, retrieve the clazz stored for given id.
   *
   * @param id the id used in setRuntimeClazz
   *
   * @return the clazz stored for this id.
   */
  public Clazz getRuntimeClazz(int id)
  {
    if (PRECONDITIONS) require
      (id < feature().runtimeClazzIdCount());

    return (Clazz) getRuntimeData(id);
  }


  /**
   * Is this a choice-type, i.e., does it directly inherit from choice?
   */
  public boolean isChoice()
  {
    return feature().isChoice();
  }


  /**
   * Is this a choice-type whose actual generics inlude ref?  If so, a field for
   * all the refs will be needed.
   */
  public boolean isChoiceWithRefs()
  {
    boolean hasRefs = false;

    if (choiceGenerics_ != null)
      {
        for (Clazz c : choiceGenerics_)
          {
            hasRefs = hasRefs || c.isRef();
          }
      }

    return hasRefs;
  }


  /**
   * Is this a choice-type whose actual generics are all refs or stateless
   * values? If so, no tag will be added, but ChoiceIdAsRef can be used.
   *
   * In case this is a choice of stateless value without any references, the
   * result will be false since in this case, it is better to use the an integer
   * stored in the tag.
   */
  public boolean isChoiceOfOnlyRefs()
  {
    boolean hasNonRefsWithState = false;

    if (choiceGenerics_ != null)
      {
        for (Clazz c : choiceGenerics_)
          {
            hasNonRefsWithState = hasNonRefsWithState || (!c.isRef() && c.hasState());
          }
      }

    return isChoiceWithRefs() && !hasNonRefsWithState;
  }


  /**
   * Obtain the actual classes of a choice.
   *
   * @return the actual clazzes of this choice clazz, in the order they appear
   * as actual generics.
   */
  private ArrayList<Clazz> choiceGenerics()
  {
    if (PRECONDITIONS) require
      (isChoice());

    ArrayList<Clazz> result = new ArrayList<>();
    for (Type t : actualGenerics(feature().choiceGenerics()))
      {
        result.add(actualClazz(t));
      }

    return result;
  }


  /**
   * Determine the index of the generic argument of this choice type that
   * matches the given static type.
   */
  public int getChoiceTag(Type staticTypeOfValue)
  {
    if (PRECONDITIONS) require
      (isChoice(),
       staticTypeOfValue.isFreeFromFormalGenerics(),
       staticTypeOfValue == Types.intern(staticTypeOfValue));

    int result = -1;
    int index = 0;
    for (Clazz g : choiceGenerics_)
      {
        if (g.type.isAssignableFrom(staticTypeOfValue))
          {
            check
              (result < 0);
            result = index;
          }
        index++;
      }
    check
      (result >= 0);

    return result;
  }

  /**
   * For a choice clazz, get the clazz that that corresponds to the generic
   * argument to choice at index id (0..n-1).
   *
   * @param id the index of the paramenter
   */
  public Clazz getChoiceClazz(int id)
  {
    if (PRECONDITIONS) require
      (isChoice(),
       id >= 0,
       id <  choiceGenerics_.size());

    return choiceGenerics_.get(id);
  }


  /**
   * For choice clazz, this gives the offset of the memory reserved for choice
   * value with given id.
   *
   * Choice values might have different offsets depending on alignment
   * constraints or to avoid GC trouble by not mixing references with
   * non-references.
   *
   * @param id the slot id.
   */
  public int choiceValOffset(int id)
  {
    if (PRECONDITIONS) require
      (isChoice(),
       id >= 0,
       id < choiceGenerics_.size());

    check
      (choiceValsOffset_ >= 0);

    // id is ignored, all vals are currently stored at the same offset:
    return choiceValsOffset_;
  }


  /**
   * For choice clazz, this gives the offset of the memory reserved for choice
   * vlues of reference type.  Unlike non-references, that might be layouted
   * differently according to alignment constraints or not mixing reference with
   * non-references to avoid GC trouble, values of reference type are always
   * using one single shared slot.
   */
  public int choiceRefValOffset()
  {
    if (PRECONDITIONS) require
      (isChoice());

    check
      (choiceValsOffset_ >= 0);

    return choiceValsOffset_;
  }


  /**
   * Perform checks on classes such as that an instantiated clazz is not the
   * target of any calls to abstract methods that are not implemented by this
   * clazz.
   */
  public void check()
  {
    if (isInstantiated_ && abstractCalled_ != null)
      {
        FeErrors.abstractFeatureNotImplemented(feature(), abstractCalled_, instantiationPos_);
      }
  }

}

/* end of file */