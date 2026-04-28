World Generation
Technical Hytale Generator
Assignments
Assigns Props to each position in a Positions field.

Official Hytale Documentation
All content on this section is provided by Hypixel Studios Canada Inc. and is presented without any substantial changes, aside from visual design adjustments by the HytaleModding Team.
Constant
Assigns a Prop to all positions.

Parameters:
Name	Description
Prop	Prop slot
FieldFunction
Allows you to select which props to assign based on a Density field and your configured delimiters. The Density's value at each position determines which delimiter's Prop to assign.;

Parameters:
Name	Description
FieldFunction	Density slot.
Delimiters	list of noise value delimiters attaching Props to value ranges of the Density field.
Min	decimal number. Lower inclusive bound of the delimiter.
Max	decimal number. Higher exclusive bound of the delimiter.
Assignments	Assignments asset slot. The assignment provigint he Props to use.
Sandwich
Allows you to select which props to assign based on their vertical (world Y) position and your configured delimiters. Depending on each position’s height in the world the Prop of the matching delimiter will be assigned.

Image showcasing sandwich tool

Parameters:
Name	Description
Delimiters	list of world height delimiter assets.
MinY	decimal number. Lower inclusive bound of the delimiter.
MaxY	decimal number. Higher exclusive bound of the delimiter.
Assignments	Assignments asset slot.
Weighted
Picks which Prop to assign randomly based on weights and a seed.

Parameters:
Name	Description
Seed	string.
SkipChance	decimal number between 0.0 and 1.0. Determines the chance to skip a position completely without assigning it a Prop.
WeightedAssignments	List of weighted Assignments slots.
Assignments	Assignments asset slot.
Imported
Imports an exported Assignments.

Parameters:
Name	Description
Name	string. The exported Assignments.
Written by Hypixel Studios Canada Inc.