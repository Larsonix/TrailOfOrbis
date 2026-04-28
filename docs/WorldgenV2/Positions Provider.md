World Generation
Technical Hytale Generator
Positions Provider
Determines an infinite 3D positions field.

Official Hytale Documentation
All content on this section is provided by Hypixel Studios Canada Inc. and is presented without any substantial changes, aside from visual design adjustments by the HytaleModding Team.
Mesh2D
Generates a mesh of random points on a 2D plane. You can set the points’ Y value.

Parameters:
Name	Description
PointGenerator	slot for a point generator asset. See the PointGenerator section below.
PointsY	the vertical Y position of every point.
PointGenerator
Generates a random point distribution in space.

Parameters:
Name	Description
Jitter	decimal number in the range [0, 0.5]. More Jitter results in more random point distribution. A good starting point is 0.2.
ScaleX	positive decimal number. Scales the distribution.
ScaleY	positive decimal number. Scales the distribution.
ScaleZ	positive decimal number. Scales the distribution.
Seed	the seed of this point generator.
Mesh3D
Generates a mesh of random points in 3D space.

Parameters:
Name	Description
PointGenerator	slot for a point generator asset. See the PointGenerator section below.
List
Allows you to manually define a static list of positions in world coordinates.

Parameters:
Name	Description
Positions	list of 3D positions.
X	integer.
Y	integer.
Z	integer.
Anchor
Anchors the origin of the child Positions field to the contextual Anchor, if one exists. For a contextual Anchor to exist, a parent of this node must produce an Anchor.

You can also reverse the effect later down the line to move the origin back to the world’s origin like in the screenshot below.

Parameters:
Name	Description
Reverse	Boolean. If true, the node will reverse the origin of the child to the world's origin, or the origin before the previous Anchor node.
Sphere
Masks out the positions that are farther than the provided range from its origin.

Parameters:
Name	Description
Range	decimal number. The maximum distance from the origin within which that Positions are kept.
FieldFunction
Enables masking out positions using Density. The delimiters determine the regions of the Density field where positions are kept.

Parameters:
Name	Description
FieldFunction	slot for a Density input.
Delimiters	list of Density value delimiters:
Min	decimal number. Lower bound of the delimiter.
Max	deimal number. Higher bound of the delimiter.
Positions	PositionsProvider slot. The positions on which the mask is being applied.
Occurrence
Discards a percentage of input positions based on a Density field. The value of the Density field at each position determines the chance that position has to be kept.;

Positions where the Density value is below or equal to 0.0 have a 0% chance of being kept.
Positions where the Density value is above or equal to 1.0 have a 100% chance of being kept.
Positions where the Density value is between 0.0 and 1.0 have a proportional percentage chance of being kept. Example: 0.4 -> 40%.
Below is a picture that shows a simple noise field used to create regions with rare oak trees and regions with dense oak trees.

Example of Occurrence PositionsProvider

Example of Occurrence PositionsProvider2

Parameters:
Name	Description
FieldFunction	Density slot. Determines the chance of keeping the positions.
Seed	string. Determines the outcome.
Positions	PositionProvider slot.
Offset
Offsets the positions by the provided vector.

Parameters:
Name	Description
OffsetX	decimal number. Vector's x.
OffsetY	decimal number. Vector's y.
OffsetZ	decimal number. Vector's z.
Positions	PositionProvider slot. Positions to offset.
BaseHeight
Vertically offsets the Positions inside the configured vertical region by the amount of blocks determined by the BaseHeight . All Positions outside the region are discarded.

A typical application of this would be to grab Positions at Y:0, and offset them up by the BaseHeight to come closer to a terrain feature. Further adjustments can then be made using an Offset PositionsProvider. The diagram below illustrates that.

Parameters:
Name	Description
BaseHeightName	string. Name of the BaseHeight to reference.
MaxYRead	decimal number. Exclusive limit of the region to grab the Positions from the input.
MinYRead	decimal number. Inclusive limit of the region to grab the Positions from the input.
Union
Combines all the positions into a single Positioins field.

Parameters:
Name	Description
Positions	list of PositionProvider slots.
SimpleHoritontal
Keeps only the positions that are within the provided range.

Parameters:
Name	Description
RangeY	The range to keep the position in.
Positions	PositionProvider slot.
Cache
Caches the output provided by the Positions slot to improve performance in certain situations. This asset can be useful to improve performance when a Positions asset is expensive and queried numerous times.

How effective this Cache is depended heavily on the use case, the provided child Positions asset and the order in which it is queried. You can get the best performance out of this Cache asset by trial and error. An example of a good use-case for this asset is at the root of an expensive Positions tree used by a Positions2D/Positions3D Density node.

I recommend that you don’t use this cache everywhere in your Positions trees but rather choose a great place at (or close to) the root of your Positions asset tree. That said, feel free to experiment.

This cache functions by saving 3D sections of space containing the Positions (points) generated by the child slot. The sections are cubes, and their size is determined by the asset’s SectionSize parameter. The number of sections allowed to be saved in the cache is determined by the CacheSize asset parameter. A safe starting value for the SectionSize parameter would be 32, and a safe starting value for the CacheSize would be 50.

Parameters:
Name	Description
SectionsSize	integer greater than 0, safe starting value would be 32. Determines the lateral size of the section's cubic volume in blocks.
CacheSize	integer 0 or greater; safe starting value would be 50. Determines the number of Sections that are allowed to be saved in memory. If provided a value of 0, then the cache is ignored, and the Positions are directly sourced from the child Positions slot.
Positions	PositionProvider slot. The output of this Positions asset is cached.
Imported
Imports an exported PositionProvider.

Name	Description
Name	string. The exported PositionsProvider.
Written by Hypixel Studios Canada Inc.