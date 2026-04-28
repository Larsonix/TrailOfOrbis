World Generation
Technical Hytale Generator
Directionality
Determines the direction to place a Prop.

Official Hytale Documentation
All content on this section is provided by Hypixel Studios Canada Inc. and is presented without any substantial changes, aside from visual design adjustments by the HytaleModding Team.
Static
Allows you to specify the only direction the Prop will be placed in.

Parameters:
Name	Description
Rotation	integer, optional with default of 0. Can only have on of these four values: 0, 90, 180 or 270.
Pattern	Pattern slot. Used to locate a suitable position for the Prop and does not affect the Prefab’s direction.
Random
Gives the Prefab random directions based on a seed.

Parameters:
Name	Description
Seed	string. Affects rotations.
Pattern	Pattern slot. Used to locate a suitable position for the Prop and does not affect the Prefab’s direction.
Pattern (note: name of the Directionality Type)
Allows the Prop to have the correct direction based on it environment. Allows you to link Prefab directions to Pattern assets for each direction.

Parameters:
Name	Description
InitialDirection	string with possible values: “N”, “S”, “E”, “W”. The direction the Props are facing in their original state. A balcony facing North in this context would mean that the balcony is looking North.
NorthPattern	Pattern slot. Defines environments where this Prop should place facing North.
SouthPattern	Pattern slot. Defines environments where this Prop should place facing South.
EastPattern	Pattern slot. Defines environments where this Prop should place facing East.
WestPattern	Pattern slot. Defines environments where this Prop should place facing West.
Seed	string. Affects direction picking where more than one direction is possible.
Written by Hypixel Studios Canada Inc.