package resources.objects.building;

import engine.resources.common.Triangle;

public class CollisionTri {
		public Triangle tri = new Triangle();
		int[] adjacentTris;
		//0 = cannot exit, 1 = can enter+exit, 2 = catches falls. Used on player structure stairs
		byte edge1, edge2, edge3;
		byte freeYAxis; // 0 = constrained to floor, 1 = free
		// isolates sections of collision floor
		int groupNumber;
}
