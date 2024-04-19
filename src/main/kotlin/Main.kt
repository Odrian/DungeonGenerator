@file:Suppress("unused", "MemberVisibilityCanBePrivate", "FoldInitializerAndIfToElvis", "SameParameterValue",
    "EnumValuesSoftDeprecate"
)
@file:OptIn(ExperimentalStdlibApi::class)

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

fun main() {
    println("Generating...")

    val maze = MazeGenerator()
    maze.generate()
    val x0 = maze.map.keys.fold(Int.MAX_VALUE) { a, b -> min(a, b.first) }
    val x1 = maze.map.keys.fold(Int.MIN_VALUE) { a, b -> max(a, b.first) }
    val width = x1 - x0 + 1

    val y0 = maze.map.keys.fold(Int.MAX_VALUE) { a, b -> min(a, b.second) }
    val y1 = maze.map.keys.fold(Int.MIN_VALUE) { a, b -> max(a, b.second) }
    val height = y1 - y0 + 1

    val list: Array<Array<RoomType?>> = Array(height) { Array(width) { null } }
    val doorR: Array<Array<DoorType?>> = Array(height) { Array(width) { null } }
    val doorD: Array<Array<DoorType?>> = Array(height) { Array(width) { null } }

    for ((coord, roomType) in maze.map.entries) {
        val x = coord.first - x0
        val y = coord.second - y0
        list[y][x] = roomType
    }
    for ((pair, doorType) in maze.doors.entries) {
        val x = pair.first.first - x0
        val y = pair.first.second - y0
        if (pair.second.first > pair.first.first) {
            doorR[y][x] = doorType
        } else {
            doorD[y][x] = doorType
        }
    }
    fun getChar(x: Int, y: Int): String {
        val type = list[y][x]
        return when (type) {
            RoomType.Start -> "S"
            RoomType.Regular -> "R"
            RoomType.Square2 -> "B"
            RoomType.Long2H -> "L"
            RoomType.Long2V -> "L"
            RoomType.Boss3 -> "E"
            RoomType.PartOfBigD -> getChar(x, y - 1)
            RoomType.PartOfBigL -> getChar(x - 1, y)
            else -> " "
        }
    }

    for (y in 0..<height) {
        for (x in 0..<width) {
            print(getChar(x, y))

            val doorType = doorR[y][x]
            when (doorType) {
                DoorType.Door -> {
                    print("---")
                }
                DoorType.Open -> {
                    print(getChar(x, y).repeat(3))
                }
                else -> {
                    print("   ")
                }
            }
        }
        println()
        for (x in 0..<width) {
            val doorType = doorD[y][x]
            when (doorType) {
                DoorType.Door -> {
                    print("|")
                }
                DoorType.Open -> {
                    print(getChar(x, y))
                }
                else -> {
                    print(" ")
                }
            }
            print("   ")
        }
        println()
    }
}

typealias Coord = Pair<Int, Int>

typealias CoordPair = Pair<Coord, Coord>

class MazeGenerator {
    val map = mutableMapOf<Coord, RoomType>()
    val doors = mutableMapOf<CoordPair, DoorType>()

    val startCoord = Coord(0, 0)

    val roomCount: Int
        get() = map.count()

    fun generate() {
        map.clear()
        doors.clear()
        map[startCoord] = RoomType.Start

        generateRoomsUpTo(20)
        generateBossRoom()
        generateRoomsUpTo(50)
        generateLoops(2)

/*
        // add something to dead ends
        val distances1 = getDistances()
        val deadends = map.keys
            .filter { getCoordDegree(it) == 1 }
            .sortedBy { distances1[it] }
        // TODO
*/
    }
    private fun generateRoomsUpTo(count: Int) {
        while (roomCount < count) {
            val coord = map.keys.random()
            if (getActualRoomType(coord) == RoomType.Boss3) continue

            val coordNear = getCoordNear(coord).randomOrNull()
            if (coordNear != null && map[coordNear] == null) {
                val builder = getRandomRoomBuilderOrNull(coordNear)
                if (builder == null) continue
                builder.placeRoomContains(this, coordNear, coord)
            }
        }
    }
    private fun generateBossRoom() {
        // put boss room in the farthest point
        val distance = getDistances()
        val bossBuilder = BossRoomBuilder()
        val bossCoord =
            map.keys
                .asSequence()
                .sortedBy { -distance[it]!! }
                .map { getCoordNear(it).map { b -> CoordPair(b, it) } }
                .flatten()
                .filter { map[it.first] == null }
                .first { bossBuilder.canPlace(this, it.first) }
        bossBuilder.placeRoomContains(this, bossCoord.first, bossCoord.second)
    }
    private fun generateLoops(loops: Int = 1) {
        val horDoor =
            map.keys.map { makeCoordPair(it, it + Coord(1, 0)) }
        val verDoor =
            map.keys.map { makeCoordPair(it, it + Coord(0, 1)) }

        val posDoors = (horDoor + verDoor)
            .filter { map[it.second] != null && doors[it] == null }
            .filter { getActualRoomType(it.first) != RoomType.Boss3 }
            .filter { getActualRoomType(it.second) != RoomType.Boss3 }
            .toMutableList()

        for (i in 0..<min(loops, posDoors.size)) {
            val distances = getAllDistances()
            posDoors.sortBy { -distances[it.first]!![it.second]!! }
            val pair = posDoors.first()
            posDoors.remove(pair)
            doors[pair] = DoorType.Door
        }
    }

    // ----- utility -----

    fun makeCoordPair(a: Coord, b: Coord): CoordPair =
        if (a < b) Pair(a, b) else Pair(b, a)

    /** return (Top, Right, Bottom, Left) */
    fun getCoordNear(coord: Coord): List<Coord> =
        listOf(Coord(0, 1), Coord(1, 0), Coord(0, -1), Coord(-1, 0)).map { coord + it }

    fun getCoordDegree(coord: Coord): Int =
        getCoordNear(coord).filter { map[it] != null }.size

    fun getActualRoomType(coord: Coord): RoomType? {
        val type = map[coord]
        if (type == RoomType.PartOfBigD)
            return getActualRoomType(coord + Coord(0, -1))
        if (type == RoomType.PartOfBigL)
            return getActualRoomType(coord + Coord(-1, 0))
        return type
    }

    fun getDistances(start: Coord = startCoord): Map<Coord, Int> {
        if (map.isEmpty()) {
            return mapOf()
        }
        val distance = mutableMapOf<Coord, Int>()
        var query = mutableListOf(start)
        distance[start] = 0
        var count = 1
        while (query.isNotEmpty()) {
            val query2 = mutableListOf<Coord>()
            for (coord in query) {
                for (coordNear in getCoordNear(coord)) {
                    if (map[coordNear] == null) continue
                    if (distance[coordNear] != null) continue

                    val door = doors[makeCoordPair(coord, coordNear)]
                    if (door == DoorType.Door || door == DoorType.Open) {
                        distance[coordNear] = count
                        query2.add(coordNear)
                    }
                }
            }
            query = query2
            count += 1
        }
        return distance
    }
    fun getAllDistances(): Map<Coord, Map<Coord, Int>> {
        val distances: MutableMap<Coord, Map<Coord, Int>> = mutableMapOf()
        map.keys.forEach { distances[it] = getDistances(it) }
        return distances
    }

    private fun getRandomRoomBuilderOrNull(coord: Coord): RoomBuilder? {
        val entries = RoomBuilderEnum.values().filter { it.builder.canPlace(this, coord) }
        if (entries.isEmpty()) return null
        if (entries.size == 1) return entries[0].builder

        val sum = entries.sumOf { it.weight }

        var index = Random.nextInt() % sum
        val answer =
            entries.first {
                index -= it.weight
                index < 0
            }
        return answer.builder
    }

    private enum class RoomBuilderEnum(val weight: Int, val builder: RoomBuilder) {
        Regular(20, RegularRoomBuilder()),
        LineH(10 / 2, Long2HRoomBuilder()),
        LineV(10 / 2, Long2VRoomBuilder()),
        Square(5, SquareRoomBuilder())
    }
}

fun coordsNear(coord1: Coord, coord2: Coord): Boolean {
    val c = coord1 - coord2
    return abs(c.first) + abs(c.second) == 1
}

interface RoomBuilder {
    fun canPlace(maze: MazeGenerator, coord: Coord) = getPlaceContains(maze, coord) != null
    fun placeRoomContains(maze: MazeGenerator, coord: Coord, prevCoord: Coord) =
        placeRoom(maze, getPlaceContains(maze, coord)!!, prevCoord)

    fun getPlaceContains(maze: MazeGenerator, coord: Coord): Coord?
    fun placeRoom(maze: MazeGenerator, coord: Coord, prevCoord: Coord)
}

class RegularRoomBuilder : RoomBuilder {
    override fun getPlaceContains(maze: MazeGenerator, coord: Coord) =
        if (maze.map[coord] == null) coord else null

    override fun placeRoom(maze: MazeGenerator, coord: Coord, prevCoord: Coord) {
        if (!coordsNear(coord, prevCoord))
            throw IllegalArgumentException("can't create door to prev room")
        val pair = maze.makeCoordPair(coord, prevCoord)
        maze.doors[pair] = DoorType.Door
        maze.map[coord] = RoomType.Regular
    }
}

open class RectangleRoomBuilder(
    val size: Pair<Int, Int>,
    val roomType: RoomType
) : RoomBuilder {
    val contains
        get() = (0..<size.first).map { x -> (0..<size.second).map { y -> Coord(x, y) } }.flatten()
    override fun getPlaceContains(maze: MazeGenerator, coord: Coord): Coord? {
        if (maze.map[coord] != null) return null

        val possibleCoords = contains.map { coord - it }.filter { canPut(maze, it) }

        return possibleCoords.randomOrNull()
    }
    fun canPut(maze: MazeGenerator, coord: Coord): Boolean {
        return contains.map { coord + it }.all { maze.map[it] == null }
    }
    override fun placeRoom(maze: MazeGenerator, coord: Coord, prevCoord: Coord) {
        contains.forEach {
            val coord0 = coord + it
            if (coordsNear(coord0, prevCoord)) {
                val pair = maze.makeCoordPair(coord0, prevCoord)
                maze.doors[pair] = DoorType.Door
            }

            maze.map[coord0] =
                if (it.first > it.second) RoomType.PartOfBigL else RoomType.PartOfBigD

            if (it + Coord(1, 0) in contains) {
                val pair = maze.makeCoordPair(coord0, coord0 + Coord(1, 0))
                maze.doors[pair] = DoorType.Open
            }
            if (it + Coord(0, 1) in contains) {
                val pair = maze.makeCoordPair(coord0, coord0 + Coord(0, 1))
                maze.doors[pair] = DoorType.Open
            }
        }
        maze.map[coord] = roomType
    }
}

class SquareRoomBuilder : RectangleRoomBuilder(
    2 to 2,
    RoomType.Square2
)

class Long2HRoomBuilder : RectangleRoomBuilder(
    2 to 1,
    RoomType.Long2H
)

class Long2VRoomBuilder : RectangleRoomBuilder(
    1 to 2,
    RoomType.Long2V
)

class BossRoomBuilder : RectangleRoomBuilder(
    3 to 3,
    RoomType.Boss3
)

enum class RoomType {
    Start,
    Regular,
    // BigRooms
    PartOfBigD, // not bottom left of big room
    PartOfBigL,
    Long2H,
    Long2V,
    Square2,
    Boss3
}

enum class DoorType {
    Door,
    Open,
    Wall,
}

// out for unreal
data class RoomData(
    var visible: Boolean,
    val coord: Coord,
    val mainRoom: Boolean,
    val doors: List<Int>, // DoorData
    val id: Int,
    val roomId: Int,
    val roomType: Int, // RoomType
)

interface AActor

interface ARoomActor : AActor {
    val roomData: RoomData
}

// For compilation
operator fun Coord.compareTo(other: Coord): Int {
    val c = this - other
    return if (c.first != 0) {
        if (c.first > 0) 1 else -1
    } else if (c.second != 0) {
        if (c.second > 0) 1 else -1
    } else {
        0
    }
}

operator fun Coord.plus(other: Coord): Coord {
    return Pair(first + other.first, second + other.second)
}

operator fun Coord.minus(other: Coord): Coord {
    return Pair(first - other.first, second - other.second)
}

fun min(a: Coord, b: Coord) = if (a < b) a else b

fun max(a: Coord, b: Coord) = if (a > b) a else b