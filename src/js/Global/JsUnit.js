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
 * A JsUnitError object, used for reporting a failed assertion.
 * @constructor
 * @param {String} msg The error message
 * @extends Error
 */
function JsUnitError( msg ){
	/**
	 * The error message.
	 * @type String
	 */
    this.message = msg || "";
}
/**
 * Returns a string representation of JsUnitError.
 * @returns {String} A string representation of JsUnitError
 */
JsUnitError.prototype.toString = function(){
    var msg = this.message;
    return this.name + ": " + msg;
}
JsUnitError.prototype = new Error();
JsUnitError.prototype.name = "JsUnitError";

/**
 * An AssertionFailedError object, used for reporting a failed assertion.
 * @constructor
 * @param {String} msg The error message
 * @param {CallStack} stack The call stack of the error
 * @extends JsUnitError
 */
function AssertionFailedError( msg, stack ){
    JsUnitError.call( this, msg );
	/**
	 * The call stack of the error.
	 * @type CallStack
	 */
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
 * @param {String} msg An optional error message
 * @param {Object} expected The expected value
 * @param {Object} actual The actual value
 * @exception {AssertionFailedError} Thrown if the expected value is not the
 * actual one
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
 * @param {String} msg An optional error message
 * @param {Object} expected The regular expression
 * @param {Object} actual The actual value
 * @exception {AssertionFailedError} Thrown if the actual value does not match
 * the regular expression
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
 * @param {String} msg An optional error message
 * @param {String} cond The condition to evaluate
 * @exception {AssertionFailedError} Thrown if the evaluation was not false
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
 * @param {String} msg An optional error message
 * @param {Object} expected The expected value
 * @param {Object} actual The actual value
 * @param {Object} tolerance The maximum difference allowed making equality check pass
 * @note This is an enhancement to JUnit 3.8
 * @exception {AssertionFailedError} Thrown if the expected value is not within
 * the tolerance of the actual one
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
 * @param {String} msg An optional error message
 * @param {Object} object The valid object
 * @exception {AssertionFailedError} Thrown if the object is not null
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
 * @param {String} msg An optional error message
 * @param {Object} expected The expected value
 * @param {Object} actual The actual value
 * @exception {AssertionFailedError} Thrown if the expected value is not the
 * actual one
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
 * @param {String} msg An optional error message
 * @param {Object} object The defined object
 * @exception {AssertionFailedError} Thrown if the object is undefined
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
 * @param {String} msg An optional error message
 * @param {Object} object The null object
 * @exception {AssertionFailedError} Thrown if the object is not null
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
 * @param {String} msg An optional error message
 * @param {Object} expected The expected value
 * @param {Object} actual The actual value
 * @exception {AssertionFailedError} Thrown if the expected value is not the
 * actual one
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
 * @param {String} msg An optional error message
 * @param {String} cond The condition to evaluate
 * @exception {AssertionFailedError} Thrown if the evaluation was not true
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
 * @param {String} msg An optional error message
 * @param {Object} object The undefined object
 * @exception {AssertionFailedError} Thrown if the object is not undefined
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
 * @param {String} msg The error message
 * @param {CallStack} stack The call stack of the error
 * @param {String} usermsg The message part of the user
 * @exception {AssertionFailedError} Is always thrown
 */
Assert.fail = function( msg, stack, usermsg ){
   throw new AssertionFailedError( ( usermsg ? usermsg + " " : "" ) + msg, stack );
}