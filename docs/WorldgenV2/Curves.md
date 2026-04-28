World Generation
Technical Hytale Generator
Curves
Curves map decimal values to other decimal values. They enable the basic f(x) = y math expression. These nodes can be used to build the functions you need in world-gen.

Official Hytale Documentation
All content on this section is provided by Hypixel Studios Canada Inc. and is presented without any substantial changes, aside from visual design adjustments by the HytaleModding Team.
Manual
You can plot points that connect with lines to create the curve. The points are connected with straight lines. The function is constant before the first point and after the last point.

Graph of a manual curve

The example above shows a function made of 3 points.

DistanceExponential
The DistanceExponential Curve has the following shape depending on the Exponent value. As this curve’s input approaches the Range value it outputs 0.0. At an input of 0.0, this curve outputs 1.0.

Graph of a distance exponential curve

Parameters:
Name	Description
Exponent	affects the curve's shape like in the diagram above.
Range	the value after which the curve outputs a constant 0.0.
DistanceS
The DistanceS Curve combines two DistanceExponent curves to produce a shape similar to the diagram below. As this curve’s input approaches the Range value it outputs 0.0. At an input of 0.0, this curve outputs 1.0. The asset’s parameters allows you to tweak the shape of the curve.

Graph of a distance S curve

Below are some examples of Positions2D terrain using this curve.

Example of distance S curve in terrain

Parameters:
Name	Description
ExponentA	floating point value greater than 0.0. Affects the curve's shape in the first half of the range.
ExponentB	floating point value greater than 0.0. Affects the curve's shape in the second half of the range.
Range	floating point value greater than 0.0. The value after which the curve outputs a constant 0.0.
Transition	optional floating point value between 0.0 and 1.0 with a default of 1.0. Values close to 0.0 create a curve with a more sudden transition between the ExponentA and ExponentB. Values of 1.0 transition from ExponentA to ExponentB over the entire curve.
TransitionSmooth	optional floating point value between 0.0 and 1.0 with a default of 1.0. Affects the shape of the transition, lower values can result in sharper curve in some situations. Experimenting with different values is encouraged to get a feel for it.
Ceiling
This curve puts a ceiling on the output of the child curve asset.

Parameters:
Name	Description
Ceiling	decimal number. The maximum value this curve will output.
Curve	curve asset slot.
Floor
This curve puts a floor on the output of the child curve asset.

Parameters:
Name	Description
Floor	decimal number. The minimum value this curve will output.
Curve	curve asset slot.
SmoothCeiling
This curve puts a ceiling on the output of the child curve asset. As the curve approaches the ceiling within the provided range it gets smoothed.

Parameters:
Name	Description
Ceiling	decimal number. The maximum value this curve will output.
Range	decimal number, greater or equal to 0. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curve.
Curve	curve asset slot.
SmoothFloor
This curve puts a floor on the output of the child curve asset. As the curve approaches the floor within the provided range it gets smoothed.

Parameters:
Name	Description
Floor	decimal number. The minimum value this curve will output.
Range	decimal number, greater or equal to 0. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curve.
Curve	curve asset slot.
SmoothClamp
This curve limits the range of the child curve asset within the provided walls. As the curve approaches the limits within the provided range it gets smoothed.

Parameters:
Name	Description
WallA	decimal number.
WallB	decimal number.
Range	decimal number, greater or equal to 0. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curve.
Curve	curve asset slot.
SmoothMax
This curve retrieves the maximum between the two curves provided. The intersection between the curves is smoothed as their values approach within the provided range.

Parameters:
Name	Description
Range	decimal number, greater or equal to 0. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curves.
CurveA	curve asset slot.
CurveB	curve asset slot.
SmoothMin
This curve retrieves the minimum between the two curves provided. The intersection between the curves is smoothed as their values approach within the provided range.

Parameters:
Name	Description
Range	decimal number, greater or equal to 0. The range determines how much smoothing is applied. A good starting value would be ¼ of the known range of your child curves.
CurveA	curve asset slot.
CurveB	curve asset slot.
Clamp
This clamps the curve between the two wall values provided. The output of this curve will never reach outside the walls.

Parameters:
Name	Description
WallA	decimal number.
WallB	decimal number.
Curve	curve asset slot.
Inverter
This inverts the child curve such that positive values become negative and negative values become positive.

Parameters:
Name	Description
Curve	curve asset slot.
Max
This outputs the maximum value of all the child curves.

Parameters:
Name	Description
Curves	list of curve asset slots.
Min
This outputs the minimum value of all the child curves.

Parameters:
Name	Description
Curves	list of curve asset slots.
Multiplier
This multiplies all the child curves’ outputs.

Parameters:
Name	Description
Curves	list of curve asset slots.
Not
This applies a logical NOT operation on the child curve. When the child’s output is 1, this will output 0, and when the child’s output is 0 this will output 1. All numbers in between are scaled accordingly.

Parameters:
Name	Description
Curve	curve asset slot.
Sum
This adds the values of all the provided curves.

Parameters:
Name	Description
Curves	list of curve asset slots.
Imported
Imports an exported Curve.

Parameters:
Name	Description
Name	string. The exported Density asset.
Written by Hypixel Studios Canada Inc.