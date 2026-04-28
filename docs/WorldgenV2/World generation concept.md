World Generation
Worldgen Tutorial
World Generation Concepts
Explores the basic concepts of world generation in the context of Hytale.

Official Hytale Documentation
All content on this section is provided by Hypixel Studios Canada Inc. and is presented without any substantial changes, aside from visual design adjustments by the HytaleModding Team.
World Generation Concepts
Generative Noise
Hytale's terrain generation is founded on this concept of Density fields, essentially these are maps of decimal values that are used to define the terrain's shape. Density fields can be built from sources of procedural noise (such as Simplex and Cellular), contextual data and processing nodes.

White
Noise

Noise based terrain
A basic terrain shape can be created by combining a set of heights with a noise field, this formula creates most of the terrain in Hytale. This is an important concept to understand as its foundational to making terrain.

Terrain
3-dimensional terrain output.

Simplex 2D
Generative 2-dimensional noise field.

Y-Curve
2-dimensional curve drawn between a height differential.

Graphs about Noise Based Terrain

Solidity
Density generates a range of values, typically between 1 and -1. Some of these values will become our terrain and others will become the negative space, or air, around our terrain.

Density
Density values are calculated at each coordinate, you can imagine this is like a cloud
Graph about
Density

Solidity
Typically, we interpret positive values account for solid terrain, and negative values the empty space
Graph about
Solidity

Function Nodes
All of our Density fields can be manipulated with a range of function nodes provided in our node editor. These have a range of functionality that can be used to make interesting terrain shapes - a list of these can be found below.

Info
Density is calculated from the sum, this can be thought of like a traditional math equation f(x) = z + y

Below we cover some basic examples that are important to understand when creating some of your first terrain.

Absolute
In this example, absolute makes all negative values positive. This makes a kind of ridged shape on a simplex noise field.

Graph about Absolute

Normalization
Its important to understand how to manipulate a noise field into a new range. This is because some functions affect the range of your field.

In this example we are normalizing our previous example so that half of the field is empty and half solid. This is done by moving our 0 value to -1, stretching the field to our new range.

Graph about Normalization

Warning
Currently the Hytale Node Editor does not support noise visualization, but a range of examples of function nodes can be found in HytaleGenerator/Biomes/Examples/

Material Providers
Materials use a number of logical function nodes to determine which block asset is used at which location. These can be run on both solid portions of the terrain field but also on negative portions to fill in a water level for example. Some examples of this can be found below.

Solidity
Splits into two Material Providers, one for all positive values which becomes solid, the other for negatives which become empty.

Queue
Creates a queue of materials with the top node coming highest in priority and the bottom the lowest priority.

Danger
The Hytale Node Editor currently sets node priority based on a node's position in the editor, this is displayed as a number in the top right corner of the node.

Props
Props enable you to generate content in a specific limited region. Hytale has a range of different Prop types that you can use to build content that gets procedurally added to the world. You can define where the content places by using a combination of Positions fields, Scanners and Patterns.

Positions
Provides the locations to scan in.

Scanner
An area or column around each position to scan, finds the locations that match the Pattern.

Pattern
The Pattern that defines what are valid positions based on the contents of the world.

Graph about
Props

Written by Hypixel Studios Canada Inc.