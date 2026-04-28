World Generation
Technical Hytale Generator
Scanners
Scans a local part of the world for valid positions.

Official Hytale Documentation
All content on this section is provided by Hypixel Studios Canada Inc. and is presented without any substantial changes, aside from visual design adjustments by the HytaleModding Team.
Origin
Only scans the origin position.

No Parameters.

ColumnLinear
Applies the Pattern to a column of blocks bound by the MinY and MaxY values. The scan is performed linearly from one end of the column to the other and returns all the Pattern validated positions. You can specify the direction of the scan and the maximum valid positions it can return. You can also make the MinY/MaxY scan range local to the scan origin Y instead of the world’s Y:0.

Example of ColumnLinear Scanner

Parameters:
Name	Description
MaxY	integer Y coordinate. Upper exclusive bound of the column.
MinY	integer Y coordinate. Lower inclusive bound of the column.
RelativeToPosition	Boolean. If true the scan range will be relative to the origin Y of the scan and not the world's Y:0.
BaseHeightName	string. Name of the BaseHeight the scan range will be relative to. If this is provided, then the RelativeToPosition is overridden.
TopDownOrder	Boolean. If true the scan will start at the top of the column's range and go down.
ResultCap	positive integer. The maximum number of valid results this scan will return
ColumnRandom
Applies the Pattern to a column of blocks bound by the MinY and MaxY values. The scan is performed randomly and returns all the valid positions. You can specify the scan strategy and the maximum valid positions it can return.

There are two strategies you can use with this Scanner:

DART_THROW: tries random blocks in the column and gathers the ones that validate the Pattern. This strategy is better for situations where there are many valid positions such as wall positions on the side of a cliff.
PICK_VALID: finds all the valid blocks in the column and randomly picks from the pile. This strategy is better for situations where there are fewer valid positions such as multiple floors stacked on top of each other.
Ultimately, it is up to you to find the right one based on your intent and observation. So try them both!

Example of ColumnRandom Scanner

Parameters:
Name	Description
MaxY	integer Y coordinate. Upper exclusive bound of the column.
MinY	integer Y coordinate. Lower inclusive bound of the column.
Strategy	string enum value: DART_THROW or PICK_VALID.
Seed	string.
ResultCap	positive integer. The maximum number of valid results this scan will return.
RelativeToPosition	Boolean. If true the scan range will be relative to the origin Y of the scan and not the world's Y:0.
BaseHeightName	string. The name of the BaseHeight the scan range will be relative to. If this is provided, then the RelativeToPosition is overridden.
Area
Useful for increasing the density of rarer props that are difficult to place.

Scans an area around the origin. This Scanner uses the ChildScanner asset slot and gradually scans it farther and farther away from the origin if it can’t find enough valid positions. It applies the ChildScanner on every column surrounding the origin up to the ScanRange distance. The ScanShape determines the shape from above of the total area it can scan. The ResultCap is the total number of valid positions this Scanner will try to find, it will start at the origin and scan more and more of the area until it finds enough results or until it runs out of area.

Parameters:
Name	Description
ScanRange	integer 0 or greater. Range in blocks from the scan origin of the area to scan. If the value is 0 then the ChildScanner is only applied on the origin column. A good starting value would be 0 and then increase it by 1 and see the results. In some situations higher values can have an impact on performance.
ScanShape	string, can be CIRCLE or SQUARE. Determines the shape of the scan area.
ResultCap	positive integer. The maximum number of valid results this scan will return
ChildScanner	Scanner slot used on each column of the area.
Imported
Imports an exported Scanner.

Parameters:
Name	Description
Name	string. The exported Scanner.
Written by Hypixel Studios Canada Inc.