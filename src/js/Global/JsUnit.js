/*
Axiom Testing Utilities
-----------------------
Modified extensively from JsUnit (Copyright (C) 1999,2000,2001,
2002,2003,2006,2007 Joerg Schaible).

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

/**
 *  @constructor
 */
function JsUnitError( msg ){
    this.message = msg || "";
}
JsUnitError.prototype.toString = function(){
    var msg = this.message;
    return this.name + ": " + msg;
}
JsUnitError.prototype = new Error();
JsUnitError.prototype.name = "JsUnitError";

/**
 *  @constructor
 */
function AssertionFailedError( msg, stack ){
    JsUnitError.call( this, msg );
    this.mCallStack = stack;
}
AssertionFailedError.prototype = new JsUnitError();
AssertionFailedError.prototype.name = "AssertionFailedError";

/**
 * A set of assert methods.
 * @constructor
 */
Assert = {};
/**
 * Asserts that two values are equal.
 * @tparam String msg An optional error message.
 * @tparam Object expected The expected value.
 * @tparam Object actual The actual value.
 * @exception AssertionFailedError Thrown if the expected value is not the
 * actual one.
 */
Assert.assertEquals = function( msg, expected, actual )
{
    if( arguments.length == 2 )
    {
        actual = expected;
        expected = msg;
        msg = null;
    }
    if( expected != actual ){
		Assert.fail( "Expected:[" + expected + "], but was:[" + actual + "]"
				   , /*new CallStack()*/'', msg );
	}
}
/**
 * Asserts that a regular expression matches a string.
 * @tparam String msg An optional error message.
 * @tparam Object expected The regular expression.
 * @tparam Object actual The actual value.
 * @exception AssertionFailedError Thrown if the actual value does not match
 * the regular expression.
 * @note This is an enhancement to JUnit 3.8
 * @since 1.3
 */
Assert.assertMatches = function( msg, expected, actual )
{
    if( arguments.length == 2 )
    {
        actual = expected;
        expected = msg;
        msg = null;
    }
    if( expected instanceof RegExp && typeof( actual ) == "string" )
    {
        if( !actual.match( expected ))
            Assert.fail( "RegExp:[" + expected + "] did not match:[" + actual + "]", /*new CallStack()*/undefined, msg );
    }
    else
        Assert.fail( "Expected:[" + expected + "], but was:[" + actual + "]"
            , /*new CallStack()*/undefined, msg );
}
/**
 * Asserts that a condition is false.
 * @tparam String msg An optional error message.
 * @tparam String cond The condition to evaluate.
 * @exception AssertionFailedError Thrown if the evaluation was not false.
 */
Assert.assertFalse = function( msg, cond )
{
    if( arguments.length == 1 )
    {
        cond = msg;
        msg = null;
    }
    if( eval( cond ))
        Assert.fail( "Condition should have failed \"" + cond + "\""
            , /*new CallStack()*/undefined, msg );
}
/**
 * Asserts that two floating point values are equal to within a given tolerance.
 * @tparam String msg An optional error message.
 * @tparam Object expected The expected value.
 * @tparam Object actual The actual value.
 * @tparam Object tolerance The maximum difference allowed to make equality check pass.
 * @note This is an enhancement to JUnit 3.8
 * @exception AssertionFailedError Thrown if the expected value is not within
 * the tolerance of the actual one.
 */
Assert.assertFloatEquals = function( msg, expected, actual, tolerance)
{
    if( arguments.length == 3 )
    {
        tolerance = actual;
        actual = expected;
        expected = msg;
        msg = null;
    }
    if(    typeof( actual ) != "number"
        || typeof( expected ) != "number"
        || typeof( tolerance ) != "number" )
    {
        Assert.fail( "Cannot compare " + expected + " and " + actual
            + " with tolerance " + tolerance + " (must all be numbers).");
    }

    if( Math.abs(expected - actual) > tolerance)
    {
        Assert.fail( "Expected:[" + expected + "], but was:[" + actual + "]"
            , /*new CallStack()*/undefined, msg );
    }
}
/**
 * Asserts that an object is not null.
 * @tparam String msg An optional error message.
 * @tparam Object object The valid object.
 * @exception AssertionFailedError Thrown if the object is not null.
 */
Assert.assertNotNull = function( msg, object )
{
    if( arguments.length == 1 )
    {
        object = msg;
        msg = null;
    }
    if( object === null )
        Assert.fail( "Object was null.", /*new CallStack()*/undefined, msg );
}
/**
 * Asserts that two values are not the same.
 * @tparam String msg An optional error message.
 * @tparam Object expected The expected value.
 * @tparam Object actual The actual value.
 * @exception AssertionFailedError Thrown if the expected value is not the
 * actual one.
 */
Assert.assertNotSame = function( msg, expected, actual )
{
    if( arguments.length == 2 )
    {
        actual = expected;
        expected = msg;
        msg = null;
    }
    if( expected === actual )
        Assert.fail( "Not the same expected:[" + expected + "]"
				   , /*new CallStack()*/undefined, msg );
}
/**
 * Asserts that an object is not undefined.
 * @tparam String msg An optional error message.
 * @tparam Object object The defined object.
 * @exception AssertionFailedError Thrown if the object is undefined.
 */
Assert.assertNotUndefined = function( msg, object )
{
    if( arguments.length == 1 )
    {
        object = msg;
        msg = null;
    }
    if( object === undefined )
        Assert.fail( "Object [" + object + "] was undefined."
				   , /*new CallStack()*/undefined, msg );
}
/**
 * Asserts that an object is null.
 * @tparam String msg An optional error message.
 * @tparam Object object The null object.
 * @exception AssertionFailedError Thrown if the object is not null.
 */
Assert.assertNull = function( msg, object )
{
    if( arguments.length == 1 )
    {
        object = msg;
        msg = null;
    }
    if( object !== null )
        Assert.fail( "Object [" + object + "] was not null."
            , /*new CallStack()*/undefined, msg );
}
/**
 * Asserts that two values are the same.
 * @tparam String msg An optional error message.
 * @tparam Object expected The expected value.
 * @tparam Object actual The actual value.
 * @exception AssertionFailedError Thrown if the expected value is not the
 * actual one.
 */
Assert.assertSame = function( msg, expected, actual )
{
    if( arguments.length == 2 )
    {
        actual = expected;
        expected = msg;
        msg = null;
    }
    if( expected === actual )
        return;
    else
        Assert.fail( "Same expected:[" + expected + "], but was:[" + actual + "]"
            , /*new CallStack()*/undefined, msg );
}
/**
 * Asserts that a condition is true.
 * @tparam String msg An optional error message.
 * @tparam String cond The condition to evaluate.
 * @exception AssertionFailedError Thrown if the evaluation was not true.
 */
Assert.assertTrue = function( msg, cond ){
    if( arguments.length == 1 ){
        cond = msg;
        msg = null;
    }
    if( !eval( cond )){
        Assert.fail( "Condition failed \"" + cond + "\"", /*new CallStack()*/undefined, msg );
	}
}
/**
 * Asserts that an object is undefined.
 * @tparam String msg An optional error message.
 * @tparam Object object The undefined object.
 * @exception AssertionFailedError Thrown if the object is not undefined.
 */
Assert.assertUndefined = function( msg, object ){
    if( arguments.length == 1 ){
        object = msg;
        msg = null;
    }
    if( object !== undefined ){
        Assert.fail( "Object [" + object + "] was not undefined."
            , /*new CallStack()*/undefined, msg );
	}
}
/**
 * Fails a test with a give message.
 * @tparam String msg The error message.
 * @tparam CallStack stack The call stack of the error.
 * @tparam String usermsg The message part of the user.
 * @exception AssertionFailedError Is always thrown.
 */
Assert.fail = function( msg, stack, usermsg ){
   throw new AssertionFailedError( ( usermsg ? usermsg + " " : "" ) + msg, stack );
}