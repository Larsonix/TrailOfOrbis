World Generation
Technical Hytale Generator
Props
Localized content that can read and write to the world.

Official Hytale Documentation
All content on this section is provided by Hypixel Studios Canada Inc. and is presented without any substantial changes, aside from visual design adjustments by the HytaleModding Team.
Box
This is a testing and debugging Prop asset that generates a box. This Prop uses a Pattern and a Scanner to locate a suitable spot relative to its provided position.

Parameters:
Name	Description
Range	3D positive integer vector. Determines the distance in blocks between the prop's center and each side.
X	distance in the x-axis
Y	distance in the y-axis
Z	distance in the z-axis
BoxBlockType	the BlockType the box is made of.
Pattern	Pattern asset slot. This defines a suitable slot.
Scanner	Scanner asset slot. Scans the surroundings for a suitable spot for this prop.
Density
Generates a Prop from a Density field and MaterialProvider asset.;

Below is an example of terrain with smaller dirt boulders on top of large stone ones.

Example of Density Prop

Example of Density Prop configuration

Parameters:
Name	Description
Range	3D positive integer vector. Determines the distance in blocks between the prop's center and each side.
X	distance in the x-axis
Y	distance in the y-axis
Z	distance in the z-axis
PlacementMask	BlockMask asset slot. Sets the rules for what blocks can be placed and what blocks can be replaced in the world.
Pattern	Pattern asset slot. This defines a suitable slot.
Scanner	Scanner asset slot. Scans the surroundings for a suitable spot for this prop.
Density	Density asset slot. This gives shape to the Prop.
Material	MaterialProvider asset slot. This determines the block types to use.
Prefab
This Prop places a Prefab in a suitable spot. This Prop uses a Directionality asset, a Pattern and a Scanner to locate a suitable spot relative to its provided position. A BlockMask asset can also be used to specify which Materials the Prefab cannot replace in the world, which Materials the Prefab cannot place, and even which world Materials cannot be replaced by specific Prefab Materials. See the BlockMask documentation.

Prefab Prop also support Molding to the terrain. To enable Molding you must specify a MoldingDirection of DOWN or UP, and provide a MoldingScanner and MoldingPattern. If you want your Prefab’s children to also mold, you need to set the MoldingChildren to true. I recommend you use a LinearScanner with the Local parameter set to true for the MoldingScanner, and limit the ResultCap to 1. In a typical ruin Prefab, the MoldingScanner and MoldingPattern can be used to find the floor or ceiling to mold the ruin to.

Parameters:
Name	Description
WeightedPrefabPaths	list of weighted paths to files or folders containing prefabs. Each path entry contains:
Path	path to the folder or file to load from.
Weight	the weight for placing a prefab from this path. If the path leads to a folder then every prefab in the folder has the same chance to be picked, once the folder has been picked. In other words, the weight applies to the entire folder.
LegacyPath	optional boolean. If true then it will look in HytaleAssets/Server/World/Default/Prefabs/{PrefabPath}, otherwise it will look in HytaleAssets/Server/WorldgenAlt/Prefabs/{PrefabPath}.
Directionality	Directionality asset slot. This defines the valid positions and the direction the Prop should place with. See the Directionality asset documentation below.
Scanner	Scanner asset slot. Scans the surroundings for a suitable spot for this prop.
BlockMask	BlockMask asset slot. Defines masking for this Prefab's block placement.
MoldingDirection	(optional, defaults to "NONE") string values "UP", "DOWN" or "NONE".
MoldingChildren	(optional) boolean. If the children also mold.
MoldingScanner	(optional) Scanner asset slot. Scanner used to match MoldingPattern.
MoldingPattern	(optional) Pattern used to find surface to mold Prefab columns to.
LoadEntities	(optional) boolean. If true, the entities from the prefabs will be loaded and placed. This configuration applies to the prefab children as well.
Column
Generates a prop that is completely contained within a column.

Parameters:
Name	Description
ColumnBlocks	list of BlockTypes and Y coordinates. The Y coordinates are relative to the Prop's origin (anchor block). Contains BlockType (string BlockType) and Y (the Y integer coordinate relative to the Prop's origin).
Directionality	Directionality asset slot. This defines the valid positions and the direction the Prop should place with. See the Directionality asset documentation below.
Scanner	Scanner asset slot.
BlockMask	BlockMask asset slot. Optional.
Cluster
Places a cluster of Column type Props relative to its origin. The DistanceCurve determines the density depending on the distance from the Cluster’s origin. The Column Props are picked from a weighted list.

The Column Prop assets provided to this Cluster Prop must use Scanner and Pattern assets that only read within the origin column because that is the only space the Cluster Prop will provide to them. If the Column Prop provided reaches outside its column, the Prop will not be used by the Cluster Prop. This is to maintain consistent Context-Dependency.

Below is a diagram showing how the density varies in function of the distance, and how the DistanceCurve affects it.

Example of Cluster Prop distance curve

The Pattern and Scanner asset slots are optional. By default, the Cluster Prop will use the Position it is assigned to. If a Pattern and Scanner are provided, it will use those to position the Cluster’s origin.

Parameters:
Name	Description
Range	integer. Determines the distance in blocks from the origin of the cluster to its limit. A good starting range is 10.
DistanceCurve	a curve that determines the chance a block will have a Column Prop based on its distance from the Cluster’s origin.
Seed	string. Used during density and Prop picking from the weighted list.
Pattern	optional Pattern asset slot, used to position the Cluster’s origin.
Scanner	optional Scanner asset slot, used to position the Cluster’s origin.
WeightedProps	list of weighted Column Props. Each element in the list contains:
Weight	floating point value above 0.0.
ColumnProp	slot for a Column Prop asset (only Column Prop assets can be used here). This Prop will only have access to a column to both scan and place. This means that you need to ensure your Scanner and Pattern assets operate within that column. If this is violated the Prop might fail to place to keep Context Dependency consistency.
Union
This Prop type places all the props in the provided list at the same position one after the other.;

Parameters:
Name	Description
Props	list of props to place together.
Offset
Offsets the position of the child Prop.

Parameters:
Name	Description
Offset	3D integer vector. The direction in which to offset the child Prop's position.
Prop	Prop slot.
Weighted
Picks which Prop to place based on a seed and weights.

Parameters:
Name	Description
Entries	Entry containing: Weight (greater than 0.0) and Prop (Prop slot).
Seed	string. The seed determining which Prop is picked. This seed also mutates the seed passed to the children.
Queue
It places the first Prop in the queue that can be placed. Whether a Prop in the queue can be placed depends on that Prop’s type and configuration. A Box Prop, for example, can only be placed if its Scanner and Pattern configuration finds a valid position.

Parameters:
Name	Description
Queue	ordered list of Prop asset slots. First entry is the highest priority.
PondFiller
It fills up depressions in the terrain with the provided MaterialProvider.

The screenshots below show a few examples of ponds generated with this Prop.\

Example of PondFiller Prop1

Example of PondFiller Prop2

You can configure the bounding box inside which it will identify and fill-in existing terrain depressions. The bounding box’s size and shape determines how large the resulting ponds can be.

The diagram below shows how the bounding box defines the outcome.

Example of PondFiller bounding box

The Pattern and Scanner can be used to position the origin of the Filler Prop like any other Prop. The origin anchors the bounding box at runtime. The ponds that fit inside that bounding box are filled with the MaterialProvider.

The BarrierBlockSet defines what the walls of the depressions are made of.

Important: The performance impact of this Prop can be relatively high depending on the size of its bounding box. Because of that, I recommend optimizing the size of its bounding box for each use-case.

Parameters:
Name	Description
BoundingMin	3D point. Defines the lowest corner of the bounding box relative to the anchor point. Contains X, Y, Z (floating point numbers).
BoundingMax	3D point asset slot. Defines the greatest corner of the bounding box relative to the anchor point. Contains X, Y, Z (floating point numbers).
BarrierBlockSet	BlockSet asset slot. Defines the types of blocks that can make up the solid terrain, through which the pond material cannot seep.
FillMaterial	MaterialProvider asset slot. Determines the types of blocks used when filling up the ponds, water for example.
Pattern	Pattern asset slot. This defines a suitable slot.
Scanner	Scanner asset slot. Scans the surroundings for a suitable spot for this prop.
Imported
Imports an exported Prop.

Parameters:
Name	Description
Name	string. The exported Prop.
Written by Hypixel Studios Canada Inc.