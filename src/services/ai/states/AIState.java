/*******************************************************************************
 * Copyright (c) 2013 <Project SWG>
 * 
 * This File is part of NGECore2.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Using NGEngine to work with NGECore2 is making a combined work based on NGEngine. 
 * Therefore all terms and conditions of the GNU Lesser General Public License cover the combination.
 ******************************************************************************/
package services.ai.states;

import java.util.NoSuchElementException;
import java.util.Vector;

import main.NGECore;
import engine.resources.scene.Point3D;
import engine.resources.scene.Quaternion;
import engine.resources.service.INetworkDispatch;
import resources.common.SpawnPoint;
import resources.objects.building.BuildingObject;
import resources.objects.cell.CellObject;
import resources.objects.creature.CreatureObject;
import resources.objects.tangible.TangibleObject;
import resources.objects.weapon.WeaponObject;
import services.ai.AIActor;
import tools.DevLog;

@SuppressWarnings("unused")
public abstract class AIState {

	public abstract byte onEnter(AIActor actor) throws Exception;
	public abstract byte onExit(AIActor actor) throws Exception;
	public abstract byte move(AIActor actor) throws Exception;
	public abstract byte recover(AIActor actor) throws Exception;
	
	public static long lastExecutionTime = 0L;
	public static long stateID = 0L;
	public static long autoStateID = 0L;
	
	
	public enum StateResult {;
	
		// default error state result
		public static final byte NONE = 0;
		// finished with state
		public static final byte FINISHED = 1;
		// unfinished with state, call recover()
		public static final byte UNFINISHED = 2;
		public static final byte DEAD = 3;
		// for deaggro or idling
		public static final byte IDLE = 4;
		public static final byte PATROL = 5;
		public static final byte LOITER = 6;
		public static final byte FOLLOW = 7;
		public static final byte ATTACK = 8;
	
	}
	
	public boolean findNewPosition(AIActor actor, float speed, float stopDistance, Point3D newPosition) {
		
		speed *= 0.5; // 2 updates per second
		CreatureObject creature = actor.getCreature();
		if (!actor.isActorAlive())
			return false; // Suppress any further movement when actor is destroyed to prevent reappearing
		NGECore core = NGECore.getInstance();
		Point3D currentPosition = creature.getPosition();
		Point3D targetPosition = null;
		// Cell management
		if (creature.getContainer()!=null && creature.getContainer() instanceof CellObject){
			CellObject cellObj1 = (CellObject)creature.getContainer();
			if (cellObj1!=null){
				currentPosition.setCell(cellObj1);
			}
		}
		
		
		float maxDistance = stopDistance;
		boolean finished = false;
		float dx, dz, newX = 0, newY = 0, newZ = 0;
		Vector<Point3D> movementPoints = actor.getMovementPoints();
		Vector<Point3D> patrolPoints = actor.getPatrolPoints();
		CellObject cell = null;
		//while(!finished && movementPoints.size() != 0) {
		
//		if ((!(actor.getCurrentState() instanceof FollowState)) && movementPoints.size() == 0 && patrolPoints.size() == 0) {
//			if (creature.getTemplate().contains("shared_dressed_tutorial_mentor.iff"))
//				System.out.println("findNewPosition targetPosition null ");
//			DevLog.debugoutai(actor, "Charon", "AI State findnewpos", "Breakout1");
//			return false;
//		}
			
		if (movementPoints.size() != 0){
			try{
				targetPosition = movementPoints.firstElement();		
			} catch (NoSuchElementException e) {
				targetPosition = currentPosition;
			}
		}
		
		targetPosition = determineTargetPosition(actor);
		
//		if (creature.getTemplate().contains("eisley_officer"))
//			NGECore.getInstance().aiService.placeMarker(targetPosition);
		
		if (targetPosition.getCell()!=null){
			int cellNumber = targetPosition.getCell().getCellNumber();
//			if (creature.getTemplate().contains("eisley_officer"))
//				System.out.println("TARGETPOS PRINT x " + targetPosition.x + " y " + targetPosition.y + " z " + targetPosition.z + " cell " + cellNumber);
		} else {
			if (creature.getTemplate().contains("eisley_officer"))
				System.out.println("HUH1?");
		}
		
		if (targetPosition==null){
			System.out.println("targetPosition==null L160 AISTATE");
			DevLog.debugoutai(actor, "Charon", "AI State findnewpos", "Breakout2");
			return false;
		}
		
		Vector<Point3D> path = core.aiService.findPath(creature.getPlanetId(), currentPosition, targetPosition);
		
		if (targetPosition==null){
			if (creature.getTemplate().contains("eisley_officer"))
				System.out.println("findNewPosition targetPosition null ");
			DevLog.debugoutai(actor, "Charon", "AI State findnewpos", "Breakout2");
			return false;
		}
		
		float distanceToTarget = targetPosition.getWorldPosition().getDistance(creature.getWorldPosition());
		
		if (targetPosition.getCell()!=null){
			Point3D targetWorldPosition = core.simulationService.convertModelSpaceToPoint(targetPosition, creature.getGrandparent());
			distanceToTarget = targetWorldPosition.getDistance(creature.getWorldPosition());
		}
		
		AIActor aiActor = (AIActor) creature.getAttachment("AI");
		TangibleObject targetObject = null;
		if (aiActor.getFollowObject()!=null){
			targetObject = aiActor.getFollowObject();
		}
		
		if (targetObject!=null && creature.getContainer() instanceof CellObject){
			distanceToTarget = creature.getWorldPosition().getDistance(targetObject.getWorldPosition());
			stopDistance = 1; // for now, later must be weapon-range dependent
		}
		

		
		// Ok here its about the target being in weapon range.
		// Inside of buildings, LOS check must yield if target is attackable
		// otherwise breadcrumbs must be followed until it is
		
		if(distanceToTarget > stopDistance) {
			maxDistance = Math.min(speed, distanceToTarget - stopDistance);
		} else {
			/*
			DevLog.debugoutai(actor, "Charon", "AI State findnewpos", "Breakout3");
			if (actor.getCurrentState().getClass().equals(RetreatState.class)){
				if(actor.getIntendedPrimaryAIState().equals(PatrolState.class)){
					actor.setCurrentState(new PatrolState());
				}	
				if(actor.getIntendedPrimaryAIState().equals(FollowState.class)){
					actor.setCurrentState(new FollowState());
				}
				if(actor.getIntendedPrimaryAIState().equals(LoiterState.class)){
					actor.setCurrentState(new LoiterState());
				}
			}
			*/		
			
			if (creature.getTemplate().contains("eisley_officer"))
				System.out.println("RETURN FALSE (distanceToTarget <= stopDistance) -> distanceToTarget" + distanceToTarget + " stopDistance " + stopDistance);
			return false;
		}
		
		Point3D oldPosition = null;
		float pathDistance = 0;
		
		if (path.size()==0 && creature.getTemplate().contains("eisley_officer"))
			System.out.println("path.size()==0 !!!!!!!!!!!");
					
		for(int i = 1; i < path.size() && !finished; i++) {
			
			Point3D currentPathPosition = path.get(i);
			cell = currentPathPosition.getCell();
			
			if(oldPosition == null)
				oldPosition = path.get(0);
			Point3D oldWorldPos = oldPosition.getWorldPosition();
			
			pathDistance += oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
			if(pathDistance >= maxDistance || i == path.size() - 1 || currentPathPosition.getCell() != creature.getContainer()) {

				finished = true;
				
				if(movementPoints.size() != 0 && currentPosition.getWorldPosition().getDistance(currentPathPosition.getWorldPosition()) <= stopDistance && cell == creature.getContainer()) {
					if(i == path.size() - 1 && movementPoints.size()>0){
						System.out.println("AHA movementpoints handled here");
						try {
							movementPoints.remove(0);
						} catch (ArrayIndexOutOfBoundsException ex){} // No idea why a vector with size>0 throws this sometimes
					}
					finished = false;
				} else {
				
					if(cell == null)
						oldPosition = oldPosition.getWorldPosition();
					else {
						if(oldPosition.getCell() == null)
							oldPosition = core.simulationService.convertPointToModelSpace(oldPosition, cell.getContainer());
					}
					
					//if(pathDistance > maxDistance) {
					if(true) {
						
						//float distance = oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
						float distance = NGECore.getInstance().aiService.distanceSquared(oldWorldPos, currentPathPosition.getWorldPosition()); // Ok to use, as distance is not directly used later
						float travelDistance = distance - (pathDistance - maxDistance);
	
						// temp fix for melee npcs
						travelDistance *= 1.3;
						if(travelDistance <= 0) {
							newX = currentPathPosition.x;
							newZ = currentPathPosition.z;
						} else {							
							if(distance > 0) {
								dx = currentPathPosition.x - oldPosition.x;
								dz = currentPathPosition.z - oldPosition.z;
								float deltaDist = getDeltaDist(dx, dz);
								if (deltaDist==0.0){
									if(creature.getTemplate().contains("eisley_officer"))
										System.out.println("deltaDist is 0.0!!!" + deltaDist + " dx " + dx + " dz " + dz);
								}
								newX = (float) (oldPosition.x + (speed * (dx / deltaDist)));
								newZ = (float) (oldPosition.z + (speed * (dz / deltaDist)));								
							} else {
								newX = currentPathPosition.x;
								newZ = currentPathPosition.z;
							}							
						}					
						if(cell == null) {
							float height = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);
							newY = height;
						} else {
							newY = currentPathPosition.y;
						}
					} else {
						if (creature.getTemplate().contains("eisley_officer"))
							System.out.println("ELSE CASE pathDistance <= maxDistance path " + pathDistance + " max " + maxDistance);
					}
				}
				
			} else {
				
				if (creature.getTemplate().contains("eisley_officer"))
					System.out.println("ELSE CASE");
				
				newX = currentPathPosition.x;
				newZ = currentPathPosition.z;
				newY = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);			
			}
			oldPosition = currentPathPosition;
		} 
		//}
		newPosition.x = newX;
		newPosition.y = newY;
		newPosition.z = newZ;
		newPosition.setCell(cell);
		
//		if (creature.getTemplate().contains("eisley_officer") && actor.getCurrentState().getClass().equals(AttackState.class)){
//			//NGECore.getInstance().aiService.placeMarker(newPosition);
//			
//			Vector<Point3D> pastPoints = (Vector<Point3D>)creature.getAttachment("Pastpoints");
//			for (Point3D curpos : pastPoints){
//				if (newPosition.x==curpos.x && newPosition.z==curpos.z){
////					((CreatureObject)actor.getFollowObject()).sendSystemMessage("Position already occured!!!",(byte)0);
////					System.out.println("Position already occured!!!");
//				} else {
////					System.out.println("Position IS NEW");
//				}
//			} 
//			
//			pastPoints.add(newPosition);
//			creature.setAttachment("Pastpoints",pastPoints);			
//		}
		return true;
	}
	
	
	
	
	
	public boolean findNewPositionOLD(AIActor actor, float speed, float stopDistance, Point3D newPosition) {
		
		speed *= 0.5; // 2 updates per second
		CreatureObject creature = actor.getCreature();
		if (!actor.isActorAlive())
			return false; // Suppress any further movement when actor is destroyed to prevent reappearing
		NGECore core = NGECore.getInstance();
		Point3D currentPosition = creature.getPosition();
		Point3D targetPosition = null;
		// Cell management
		if (creature.getContainer()!=null && creature.getContainer() instanceof CellObject){
			CellObject cellObj1 = (CellObject)creature.getContainer();
			if (cellObj1!=null){
				currentPosition.setCell(cellObj1);
			}
		}
		
//		if (creature.getGrandparent()!=null)
//			((BuildingObject)creature.getGrandparent()).getCellForPosition(creature.getPosition());
				
		float maxDistance = stopDistance;
		boolean finished = false;
		float dx, dz, newX = 0, newY = 0, newZ = 0;
		Vector<Point3D> movementPoints = actor.getMovementPoints();
		Vector<Point3D> patrolPoints = actor.getPatrolPoints();
		CellObject cell = null;
		
		if ((!(actor.getCurrentState() instanceof FollowState)) && movementPoints.size() == 0 && patrolPoints.size() == 0) {
			if (creature.getTemplate().contains("eisley_officer"))
				System.out.println("findNewPosition targetPosition null ");
			DevLog.debugoutai(actor, "Charon", "AI State findnewpos", "Breakout1");
			return false;
		}
			
//		if (movementPoints.size() != 0){
//			try{
//				targetPosition = movementPoints.firstElement();
//			} catch (NoSuchElementException e) {
//				targetPosition = currentPosition;
//			}
//		}
//		
		targetPosition = determineTargetPosition(actor);
		if (targetPosition.getCell()!=null){
			int cellNumber = targetPosition.getCell().getCellNumber();
			System.out.println("TARGETPOS PRINT x " + targetPosition.x + " y " + targetPosition.y + " z " + targetPosition.z + " cell " + cellNumber);
		}
		
		if (targetPosition==null){
			System.out.println("targetPosition==null L160 AISTATE");
			DevLog.debugoutai(actor, "Charon", "AI State findnewpos", "Breakout2");
			return false;
		}
		
		Vector<Point3D> path = core.aiService.findPath(creature.getPlanetId(), currentPosition, targetPosition);
				
		float distanceToTarget = targetPosition.getWorldPosition().getDistance(creature.getWorldPosition());
		
		if (targetPosition.getCell()!=null){
			//Point3D targetWorldPosition = core.simulationService.convertModelSpaceToPoint(targetPosition, creature.getGrandparent());
			Point3D targetWorldPosition = targetPosition.getWorldPosition();
			distanceToTarget = targetWorldPosition.getDistance(creature.getWorldPosition());
		}
		
		if (creature.getWorldPosition()!= null && actor.getFollowObject()!=null)
			distanceToTarget = creature.getWorldPosition().getDistance(actor.getFollowObject().getWorldPosition());
		
		if(distanceToTarget > stopDistance) {
			maxDistance = Math.min(speed, distanceToTarget - stopDistance);
		} else { 
			System.err.println(" (distanceToTarget <= stopDistance) distanceToTarget " + distanceToTarget + " stopDistance " + stopDistance);
			return false;
		}
		
		Point3D oldPosition = null;
		float pathDistance = 0;
					
			
			Point3D currentPathPosition = targetPosition;
			cell = currentPathPosition.getCell();
			
			if(oldPosition == null)
				oldPosition = currentPosition;
			Point3D oldWorldPos = oldPosition.getWorldPosition();
			
			pathDistance += oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
//			if(pathDistance >= maxDistance || currentPathPosition.getCell() != creature.getContainer()) {
			if(true) {

				if(movementPoints.size() != 0 && currentPosition.getWorldPosition().getDistance(currentPathPosition.getWorldPosition()) <= stopDistance && cell == creature.getContainer()) {
					if(movementPoints.size()>0){
						try {
							System.out.println("movementPoints.remove(0);");
							movementPoints.remove(0);
						} catch (ArrayIndexOutOfBoundsException ex){} // No idea why a vector with size>0 throws this sometimes
					}

				} else {
				
					if(cell == null)
						oldPosition = oldPosition.getWorldPosition();
					else {
						if(oldPosition.getCell() == null)
							oldPosition = core.simulationService.convertPointToModelSpace(oldPosition, cell.getContainer());
					}
					
					//if(pathDistance > maxDistance) {
					if(true) {
						
						float distance = NGECore.getInstance().aiService.distanceSquared(oldWorldPos, currentPathPosition.getWorldPosition()); // Ok to use, as distance is not directly used later
						float travelDistance = distance - (pathDistance - maxDistance);
	
						// temp fix for melee npcs
						travelDistance *= 1.3;
						if(travelDistance <= 0) {
							newX = currentPathPosition.x;
							newZ = currentPathPosition.z;
							
							if (creature.getTemplate().contains("eisley_officer") && creature.getGrandparent() instanceof BuildingObject)
								System.out.println("travelDistance <= 0");
							
						} else {
							
							if(distance > 0) {
								dx = currentPathPosition.x - oldPosition.x;
								dz = currentPathPosition.z - oldPosition.z;
								if (creature.getTemplate().contains("eisley_officer"))
									System.out.println("dx " + dx + " dz " + dz);

								float deltaDist = getDeltaDist(dx, dz);
								newX = (float) (oldPosition.x + (speed * (dx / deltaDist)));
								newZ = (float) (oldPosition.z + (speed * (dz / deltaDist)));								
							} else {
								System.out.println("DISTANCE<=0!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
								newX = currentPathPosition.x;
								newZ = currentPathPosition.z;
							}							
						}						
						if(cell == null) {
							float height = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);
							newY = height;
						} else {
							newY = currentPathPosition.y;
							if (creature.getContainer()!=targetPosition.getCell()){
//								if (creature.getGrandparent() instanceof BuildingObject)
//									cell = ((BuildingObject)creature.getGrandparent()).getCellForPosition(new Point3D(newX,newY,newZ), creature);
							}
						}
					} else {System.out.println("ELSE CASE pathDistance <= maxDistance");}
				}				
			} else {
				
				// This case must be prevented! It warps the NPC to the player
//				newX = currentPathPosition.x;
//				newZ = currentPathPosition.z;
//				newY = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);	
//				System.out.println("ELSE newX = currentPathPosition.x;");
			}
			oldPosition = currentPathPosition;

		newPosition.x = newX;
		newPosition.y = newY;
		newPosition.z = newZ;

		newPosition.setCell(cell);
		
		if (Float.isNaN(newPosition.x) || Float.isNaN(newPosition.z))
			System.out.println("NaN EndofFindPosition");

		return true;
	}
	
	public Point3D determineTargetPosition(AIActor actor){
		Point3D targetPosition = null;
		CreatureObject creature = actor.getCreature();
		Vector<Point3D> patrolPoints = actor.getPatrolPoints();
		if (actor.getCurrentState().getClass().equals(PatrolState.class)){
			targetPosition = patrolPoints.get(actor.getPatrolPointIndex());
		}		
		if (actor.getCurrentState().getClass().equals(WithdrawalState.class)){
			targetPosition = patrolPoints.get(actor.getPatrolPointIndex());
		}		
		if (actor.getCurrentState().getClass().equals(FollowState.class)){
			targetPosition = actor.getFollowObject().getWorldPosition();
			if (actor.getFollowObject().getContainer()!=null && actor.getFollowObject().getContainer() instanceof CellObject){
				CellObject cellObj1 = (CellObject)actor.getFollowObject().getContainer();
				if (cellObj1!=null)
					targetPosition.setCell(cellObj1);
			}
		}
		
		if (actor.getCurrentState().getClass().equals(AttackState.class) || actor.getCurrentState().getClass().equals(RetreatState.class) || actor.getCurrentState().getClass().equals(RepositionState.class)){
			
			if (actor.getFollowObject()!=null){
				targetPosition = actor.getFollowObject().getWorldPosition();
				if (actor.getFollowObject().getContainer()!=null)
					targetPosition = actor.getFollowObject().getPosition();
			}
			
			if (actor.getFollowObject() instanceof CreatureObject){
				Point3D crumbPos = NGECore.getInstance().aiService.findClosestBreadCrumb(creature,((CreatureObject)actor.getFollowObject()));
				if (crumbPos!=null){
					targetPosition = crumbPos;
					if (crumbPos.getCell()!=null)
						if (creature.getTemplate().contains("eisley_officer"))
							System.out.println("Closed crumb found in distance " + NGECore.getInstance().aiService.distanceSquared2D(creature.getPosition(),targetPosition) + " in cell " + crumbPos.getCell().getCellNumber());
				} else {
					// No crumb found -> bad
					System.out.println("No crumb found -> bad");
					if (actor.getFollowObject()!=null){
						targetPosition = actor.getFollowObject().getWorldPosition();
						if (actor.getFollowObject().getContainer()!=null)
							targetPosition = actor.getFollowObject().getPosition();
					}
				}
			}
		}
		
		
//		if (actor.getCurrentState().getClass().equals(AttackState.class) || actor.getCurrentState().getClass().equals(RetreatState.class) || actor.getCurrentState().getClass().equals(RepositionState.class)){
//			//System.out.println("OK HES GENERALLY IN HERE!");
//			if (actor.getFollowObject()!=null){
//				targetPosition = actor.getFollowObject().getWorldPosition();
//				if (actor.getFollowObject().getContainer()!=null)
//					targetPosition = actor.getFollowObject().getPosition();
//				
//				if (Float.isNaN(targetPosition.x) || Float.isNaN(targetPosition.z))
//					System.out.println("NaN in determineTargetPosition1");
//				
//				if (creature.getContainer()!=null && creature.getGrandparent() instanceof BuildingObject){
//					CellObject cellObj1 = null;
//					if (actor.getFollowObject() instanceof CreatureObject && actor.getFollowObject()!=null){
//						if (((CreatureObject)actor.getFollowObject()).isPlayer())
//							cellObj1 = ((CreatureObject)actor.getFollowObject()).getPosition().getCell();
////						else
////							cellObj1 = ((BuildingObject)creature.getGrandparent()).getCellForPosition(targetPosition, creature);
//					}
//					if (cellObj1!=null){
//						targetPosition.setCell(cellObj1);
//						//((CreatureObject)actor.getFollowObject()).sendSystemMessage("CellID : " + cellObj1.getCellNumber(), (byte)0);
//						//System.out.println("CELL FOUND! " + cellObj1.getCellNumber());
//					} else {System.out.println("NO CELL FOUND! ");}
//				} else {
////					if (creature.getContainer()==null)
////						System.out.println("creature.getContainer()==null");
////					if (!(creature.getGrandparent() instanceof BuildingObject))
////						System.out.println("! creature.getGrandparent() instanceof BuildingObject");					
//				}
//				if (actor.getFollowObject() instanceof CreatureObject){
//					Point3D crumbPos = NGECore.getInstance().aiService.findClosestBreadCrumb(creature,((CreatureObject)actor.getFollowObject()));
//					if (crumbPos!=null){
//						targetPosition = crumbPos;
////						if (crumbPos.getCell()!=null)
////							System.out.println("Closed crumb found target.getContainer() " + NGECore.getInstance().aiService.distanceSquared2D(creature.getPosition(),targetPosition) + " cell " + crumbPos.getCell().getCellNumber());
//					}
//				}
//			} else {System.out.println("actor.getFollowObject().getContainer()!=null");}
//		}
		if (Float.isNaN(targetPosition.x) || Float.isNaN(targetPosition.z))
			System.out.println("NaN in determineTargetPosition2");
		return targetPosition;
	}
	
	public float getDeltaDist(float dx, float dz){
		float deltaDist = 0; //float deltaDist = (float) Math.sqrt((dx * dx) + (dz * dz)); 
		int fullong=0; //  --> Approximation->faster
		int halfshort=0;
		float tempdx = dx;
		float tempdz = dz;
		if (tempdx<0)
			tempdx*=-1;
		if (tempdz<0)
			tempdz*=-1;
		if(tempdx > tempdz) {
			fullong = (int)(tempdx*100);
			halfshort = ((int)(tempdz*100)) >> 1;
		} else {
			fullong = (int)(tempdz*100);
			halfshort = ((int)(tempdx*100)) >> 1;
		}
		deltaDist = (float)(fullong + halfshort)/100.0F; // end of approximation
		return deltaDist;
	}
		
	public boolean findNewLOSPosition(AIActor actor, float speed, float stopDistance, Point3D newPosition) {
		
		speed *= 0.5; // 2 updates per second
		CreatureObject creature = actor.getCreature();
		NGECore core = NGECore.getInstance();
		Point3D currentPosition = creature.getPosition();
		Point3D targetPosition = null;
		float maxDistance = stopDistance;
		boolean finished = false;
		float dx, dz, newX = 0, newY = 0, newZ = 0;
		Vector<Point3D> movementPoints = actor.getMovementPoints();
		Vector<Point3D> patrolPoints = actor.getPatrolPoints();
		CellObject cell = null;
		//while(!finished && movementPoints.size() != 0) {
		
		if ((!(actor.getCurrentState() instanceof FollowState)) && movementPoints.size() == 0 && patrolPoints.size() == 0) {
			return false;
			}
			
			if (movementPoints.size() != 0){
				try{
					targetPosition = movementPoints.firstElement();
				} catch (NoSuchElementException e) {
					targetPosition = currentPosition;
				}
			}
			
			if (actor.getCurrentState().getClass().equals(PatrolState.class)){
				targetPosition = patrolPoints.get(actor.getPatrolPointIndex());
			}
			
			if (actor.getCurrentState().getClass().equals(FollowState.class)){
				targetPosition = actor.getFollowObject().getWorldPosition();
			}
			
			Vector<Point3D> path = core.aiService.findPath(creature.getPlanetId(), currentPosition, targetPosition);
			
			if (targetPosition==null)
				return false;
			if (actor.getFollowObject()==null)
				return false;
			
			int attempts = 0;
			float LOSdistance = targetPosition.getWorldPosition().getDistance(actor.getFollowObject().getWorldPosition());
			Point3D LOSorigin = actor.getFollowObject().getWorldPosition();
			float deltaX = actor.getFollowObject().getWorldPosition().x-targetPosition.getWorldPosition().x;
			float deltaZ = actor.getFollowObject().getWorldPosition().z-targetPosition.getWorldPosition().z;
			float originAngle = (float) (Math.atan2(deltaX,deltaZ));
			int sign = 0;
			while (!core.simulationService.checkLineOfSight(actor.getFollowObject(),targetPosition) && attempts<160){
				
				float angle = 0;
				if (sign==0){
					angle = originAngle + attempts;
					sign = 1;
				} else {
					angle = originAngle - attempts;
					sign = 0;
				}
				if (actor.getFollowObject() instanceof CreatureObject){
					if (((CreatureObject)actor.getFollowObject()).isPlayer()){
						System.out.println("CORRECTING LOS");
					}
				}
						 
				
				targetPosition = new Point3D((float) (LOSorigin.x + LOSdistance * Math.cos(angle)), 0, (float) (LOSorigin.z + LOSdistance * Math.sin(angle)));
				attempts++;
			}
			
				
			
			float distanceToTarget = targetPosition.getWorldPosition().getDistance(creature.getWorldPosition());
			
			if(distanceToTarget > stopDistance) {
				maxDistance = Math.min(speed, distanceToTarget - stopDistance);
			} else {
				return false;
			}
			
			Point3D oldPosition = null;
			float pathDistance = 0;
			
			
			
			for(int i = 1; i < path.size() && !finished; i++) {
				
				Point3D currentPathPosition = path.get(i);
				cell = currentPathPosition.getCell();
				
				if(oldPosition == null)
					oldPosition = path.get(0);
				Point3D oldWorldPos = oldPosition.getWorldPosition();
				
				pathDistance += oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
				if(pathDistance >= maxDistance || i == path.size() - 1 || currentPathPosition.getCell() != creature.getContainer()) {
					
					finished = true;
					
					if(movementPoints.size() != 0 && currentPosition.getWorldPosition().getDistance(currentPathPosition.getWorldPosition()) <= stopDistance && cell == creature.getContainer()) {
						if(i == path.size() - 1)
							movementPoints.remove(0);
						finished = false;
					} else {
					
						if(cell == null)
							oldPosition = oldPosition.getWorldPosition();
						else {
							if(oldPosition.getCell() == null)
								oldPosition = core.simulationService.convertPointToModelSpace(oldPosition, cell.getContainer());
						}
						
						if(pathDistance > maxDistance) {
							
							float distance = oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
							float travelDistance = distance - (pathDistance - maxDistance);
							// temp fix for melee npcs
							travelDistance *= 1.3;
							if(travelDistance <= 0) {
								newX = currentPathPosition.x;
								newZ = currentPathPosition.z;
							} else {
								
								if(distance > 0) {
									dx = currentPathPosition.x - oldPosition.x;
									dz = currentPathPosition.z - oldPosition.z;
									float deltaDist = (float) Math.sqrt((dx * dx) + (dz * dz));
									newX = (float) (oldPosition.x + (speed * (dx / deltaDist)));
									newZ = (float) (oldPosition.z + (speed * (dz / deltaDist)));
									
								} else {
									newX = currentPathPosition.x;
									newZ = currentPathPosition.z;
								}
								
							}
							
							if(cell == null) {
								float height = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);
								newY = height;
							} else {
								newY = currentPathPosition.y;
							}
						}
					}
					
				} else {
					newX = currentPathPosition.x;
					newZ = currentPathPosition.z;
					newY = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);			
				}
				oldPosition = currentPathPosition;
			}
		//}
		newPosition.x = newX;
		newPosition.y = newY;
		newPosition.z = newZ;
		newPosition.setCell(cell);

		return true;
	}
	
	
	public boolean findNewLoiterPosition(AIActor actor, float speed, float stopDistance, Point3D newPosition) {
		
		speed *= 0.5; // 2 updates per second
		CreatureObject creature = actor.getCreature();
		NGECore core = NGECore.getInstance();
		Point3D currentPosition = creature.getPosition();
		Point3D targetPosition = null;
		float maxDistance = stopDistance;
		boolean finished = false;
		float dx, dz, newX = 0, newY = 0, newZ = 0;
		Vector<Point3D> movementPoints = actor.getMovementPoints();
		CellObject cell = null;
		
			targetPosition = actor.getLoiterDestination(); 

			Vector<Point3D> path = core.aiService.findPath(creature.getPlanetId(), currentPosition, targetPosition);
			
			float distanceToTarget = targetPosition.getWorldPosition().getDistance(creature.getWorldPosition());
			
			if(distanceToTarget > stopDistance) {
				maxDistance = Math.min(speed, distanceToTarget - stopDistance);
			} else {
				return false;
			}
			
			Point3D oldPosition = null;
			float pathDistance = 0;
			
			for(int i = 1; i < path.size() && !finished; i++) {
				
				Point3D currentPathPosition = path.get(i);
				cell = currentPathPosition.getCell();
				
				if(oldPosition == null)
					oldPosition = path.get(0);
				Point3D oldWorldPos = oldPosition.getWorldPosition();
				
				pathDistance += oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
				if(pathDistance >= maxDistance || i == path.size() - 1 || currentPathPosition.getCell() != creature.getContainer()) {
					
					finished = true;
					
					if(currentPosition.getWorldPosition().getDistance(currentPathPosition.getWorldPosition()) <= stopDistance && cell == creature.getContainer()) {
//						if(i == path.size() - 1)
//							movementPoints.remove(0);
						finished = false;
					} else {
					
						if(cell == null)
							oldPosition = oldPosition.getWorldPosition();
						else {
							if(oldPosition.getCell() == null)
								oldPosition = core.simulationService.convertPointToModelSpace(oldPosition, cell.getContainer());
						}
						
						if(pathDistance > maxDistance) {
							
							float distance = oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
							float travelDistance = distance - (pathDistance - maxDistance);
							// temp fix for melee npcs
							travelDistance *= 1.3;
							if(travelDistance <= 0) {
								newX = currentPathPosition.x;
								newZ = currentPathPosition.z;
							} else {
								
								if(distance > 0) {
									dx = currentPathPosition.x - oldPosition.x;
									dz = currentPathPosition.z - oldPosition.z;
									float deltaDist = (float) Math.sqrt((dx * dx) + (dz * dz));
									newX = (float) (oldPosition.x + (speed * (dx / deltaDist)));
									newZ = (float) (oldPosition.z + (speed * (dz / deltaDist)));
									
								} else {
									newX = currentPathPosition.x;
									newZ = currentPathPosition.z;
								}
								
							}
							
							if(cell == null) {
								float height = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);
								newY = height;
							} else {
								newY = currentPathPosition.y;
							}
						}
					}
					
				} else {
					newX = currentPathPosition.x;
					newZ = currentPathPosition.z;
					newY = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);			
				}
				oldPosition = currentPathPosition;
			}

		newPosition.x = newX;
		newPosition.y = newY;
		newPosition.z = newZ;
		newPosition.setCell(cell);

		return true;
	}
	
	public boolean findNewFollowPosition(AIActor actor, float speed, float stopDistance, Point3D newPosition) {
		
		speed *= 0.5; // 2 updates per second
		CreatureObject creature = actor.getCreature();
		NGECore core = NGECore.getInstance();
		Point3D currentPosition = creature.getPosition();
		Point3D targetPosition = null;
		float maxDistance = stopDistance;
		boolean finished = false;
		float dx, dz, newX = 0, newY = 0, newZ = 0;
		Vector<Point3D> movementPoints = actor.getMovementPoints();
		Vector<Point3D> patrolPoints = actor.getPatrolPoints();
		CellObject cell = null;
		//while(!finished && movementPoints.size() != 0) {
		
		if ((!(actor.getCurrentState() instanceof FollowState)) && movementPoints.size() == 0 && patrolPoints.size() == 0) {
			return false;
			}
			
			if (movementPoints.size() != 0)
					targetPosition = movementPoints.firstElement();
			
			if (actor.getCurrentState().getClass().equals(PatrolState.class)){
				targetPosition = patrolPoints.get(actor.getPatrolPointIndex());
			}
			
			if (actor.getCurrentState().getClass().equals(FollowState.class)){
				targetPosition = actor.getFollowObject().getWorldPosition();
			}
			
			Vector<Point3D> path = core.aiService.findPath(creature.getPlanetId(), currentPosition, targetPosition);
			
			float distanceToTarget = targetPosition.getWorldPosition().getDistance(creature.getWorldPosition());
			
			if(distanceToTarget > stopDistance) {
				maxDistance = Math.min(speed, distanceToTarget - stopDistance);
			} else {
				return false;
			}
			
			Point3D oldPosition = null;
			float pathDistance = 0;
			
			
			
			for(int i = 1; i < path.size() && !finished; i++) {
				
				Point3D currentPathPosition = path.get(i);
				cell = currentPathPosition.getCell();
				
				if(oldPosition == null)
					oldPosition = path.get(0);
				Point3D oldWorldPos = oldPosition.getWorldPosition();
				
				pathDistance += oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
				if(pathDistance >= maxDistance || i == path.size() - 1 || currentPathPosition.getCell() != creature.getContainer()) {
					
					finished = true;
					
					if(movementPoints.size() != 0 && currentPosition.getWorldPosition().getDistance(currentPathPosition.getWorldPosition()) <= stopDistance && cell == creature.getContainer()) {
						if(i == path.size() - 1)
							movementPoints.remove(0);
						finished = false;
					} else {
					
						if(cell == null)
							oldPosition = oldPosition.getWorldPosition();
						else {
							if(oldPosition.getCell() == null)
								oldPosition = core.simulationService.convertPointToModelSpace(oldPosition, cell.getContainer());
						}
						
						if(pathDistance > maxDistance) {
							
							float distance = oldWorldPos.getDistance(currentPathPosition.getWorldPosition());
							float travelDistance = distance - (pathDistance - maxDistance);
							// temp fix for melee npcs
							travelDistance *= 1.3;
							if(travelDistance <= 0) {
								newX = currentPathPosition.x;
								newZ = currentPathPosition.z;
							} else {
								
								if(distance > 0) {
									dx = currentPathPosition.x - oldPosition.x;
									dz = currentPathPosition.z - oldPosition.z;
									float deltaDist = (float) Math.sqrt((dx * dx) + (dz * dz));
									newX = (float) (oldPosition.x + (speed * (dx / deltaDist)));
									newZ = (float) (oldPosition.z + (speed * (dz / deltaDist)));
									
								} else {
									newX = currentPathPosition.x;
									newZ = currentPathPosition.z;
								}
								
							}
							
							if(cell == null) {
								float height = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);
								newY = height;
							} else {
								newY = currentPathPosition.y;
							}
						}
					}
					
				} else {
					newX = currentPathPosition.x;
					newZ = currentPathPosition.z;
					newY = core.terrainService.getHeight(creature.getPlanetId(), newX, newZ);			
				}
				oldPosition = currentPathPosition;
			}
		//}
		newPosition.x = newX;
		newPosition.y = newY;
		newPosition.z = newZ;
		newPosition.setCell(cell);

		return true;
	}
	
	public void doMove(AIActor actor) {
		
		NGECore core = NGECore.getInstance();

		CreatureObject creature = actor.getCreature();
		if(creature.getPosture() == 14 || creature.getPosture() == 13) {
			actor.setFollowObject(null);
			return;
		}
		TangibleObject target = actor.getFollowObject();
		float speed = (float) creature.getRunSpeed();
		if (creature.getAttachment("IsSlowVehicle")!=null){
			speed = (float) creature.getWalkSpeed();
		}
		float maxDistance = determineMaxDistance(creature);
		
		boolean LOSCondition = false;
		if (target!=null){
			LOSCondition = !core.simulationService.checkLineOfSight(creature,target);
			if (creature.getContainer()!=null)
				LOSCondition = !core.simulationService.checkLineOfSightInBuilding(target,creature,creature.getGrandparent());
		}
		
		if(target != null && LOSCondition)
			maxDistance = 1;
		if (target!=null && target.getContainer()!=null){
			if (target.getPosition().getCell()!=creature.getPosition().getCell())
				maxDistance = 1; // Not in same cell
		}
		
		Point3D newPosition = new Point3D();		
		boolean foundNewPos = false;
		foundNewPos = findNewPosition(actor, speed, maxDistance, newPosition);

		if(!foundNewPos || (newPosition.x == 0 && newPosition.z == 0) || (Float.isInfinite(newPosition.x) || Float.isInfinite(newPosition.z))) {
			if (creature.getTemplate().contains("eisley_officer")){
				System.err.println("EXITING GHEEEEEEEEEEE foundNewPos " + foundNewPos + " newPosition.x " + newPosition.x);
			}
			return;
		}
		
		Point3D newWorldPos = newPosition;
		if (newPosition.getCell()==null)
			newWorldPos = newPosition.getWorldPosition();				
		
		if (creature.getTemplate().contains("eisley_officer")){
			//float direction = (float) Math.atan2(newWorldPos.x - creature.getPosition().x, newWorldPos.z - creature.getPosition().z);
			float direction = (float) Math.atan2(newPosition.x - creature.getPosition().x, newPosition.z - creature.getPosition().z);
			if(direction < 0)
				direction = (float) (2 * Math.PI + direction);
			((CreatureObject)actor.getFollowObject()).sendSystemMessage("DIRECTION : " + (Math.toDegrees(direction)-80), (byte)0);
		}
		
		//Quaternion quaternion = directionToQuaternion(creature.getPosition(), newWorldPos);   
		Quaternion quaternion = directionToQuaternion(creature.getPosition(), newPosition);  
		if (creature.getTemplate().contains("eisley_officer"))
			System.out.println("simulationService.moveObject! " + System.currentTimeMillis() + " to x " + newPosition.x + " z " + newPosition.z);
		core.simulationService.moveObject(creature, newPosition, quaternion, creature.getMovementCounter(), speed, newPosition.getCell());	
	}
	
	public Quaternion directionToQuaternion(Point3D currentPosition, Point3D newWorldPos){
		Quaternion quaternion = null;
		float direction = (float) Math.atan2(newWorldPos.x - currentPosition.x, newWorldPos.z - currentPosition.z);
		if(direction < 0)
			direction = (float) (2 * Math.PI + direction);
		quaternion = new Quaternion((float) Math.cos(direction / 2), 0, (float) Math.sin(direction / 2), 0);
        if (quaternion.y < 0.0f && quaternion.w > 0.0f) {
        	quaternion.y *= -1;
        	quaternion.w *= -1;
        }
		return quaternion;
	}
	
	public float determineMaxDistance(CreatureObject creature){
		float maxDistance = 6;
		if(creature.getWeaponId() != 0) {
			WeaponObject weapon = (WeaponObject) NGECore.getInstance().objectService.getObject(creature.getWeaponId());
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		} else if(creature.getSlottedObject("default_weapon") != null) {
			WeaponObject weapon = (WeaponObject) creature.getSlottedObject("default_weapon");
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		}
		return maxDistance;
	}
	
	public void doReposition(AIActor actor){
		
		NGECore core = NGECore.getInstance();

		CreatureObject creature = actor.getCreature();
		if(creature.getPosture() == 14 || creature.getPosture() == 13) {
			actor.setFollowObject(null);
			return;
		}
		TangibleObject target = actor.getLastTarget();
		float speed = (float) creature.getRunSpeed();
		float maxDistance = 6;
		if(creature.getWeaponId() != 0) {
			WeaponObject weapon = (WeaponObject) core.objectService.getObject(creature.getWeaponId());
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		} else if(creature.getSlottedObject("default_weapon") != null) {
			WeaponObject weapon = (WeaponObject) creature.getSlottedObject("default_weapon");
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		}
		Point3D currentPosition = creature.getWorldPosition();
		
		if(target != null && !core.simulationService.checkLineOfSight(creature, target))
			maxDistance = 1;
		
		Point3D newPosition = actor.getRepositionLocation();

		if(newPosition.x == 0 && newPosition.z == 0)
			return;
		
		Point3D newWorldPos = newPosition.getWorldPosition();
		float direction = (float) Math.atan2(newWorldPos.x - currentPosition.x, newWorldPos.z - currentPosition.z);
		if(direction < 0)
			direction = (float) (2 * Math.PI + direction);
		Quaternion quaternion = new Quaternion((float) Math.cos(direction / 2), 0, (float) Math.sin(direction / 2), 0);
        if (quaternion.y < 0.0f && quaternion.w > 0.0f) {
        	quaternion.y *= -1;
        	quaternion.w *= -1;
        }
        
		core.simulationService.moveObject(creature, newPosition, quaternion, creature.getMovementCounter(), speed, newPosition.getCell());
		
	}
	
	public void doPatrol(AIActor actor) {
		//NGECore.getInstance().aiService.logAI("AI STATE doPatrol");
		
		NGECore core = NGECore.getInstance();

		CreatureObject creature = actor.getCreature();

		if(creature.getPosture() == 14 || creature.getPosture() == 13) {
			actor.setFollowObject(null);
			return;
		}
		
		if (creature.isInCombat()){
			return;
		}

		TangibleObject target = actor.getFollowObject();
		float speed = (float) creature.getWalkSpeed();
		float maxDistance = 6;
		if(creature.getWeaponId() != 0) {
			WeaponObject weapon = (WeaponObject) core.objectService.getObject(creature.getWeaponId());
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		} else if(creature.getSlottedObject("default_weapon") != null) {
			WeaponObject weapon = (WeaponObject) creature.getSlottedObject("default_weapon");
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		}
		Point3D currentPosition = creature.getWorldPosition();
		
//		if(target != null && !core.simulationService.checkLineOfSight(creature, target))
//			maxDistance = 1;
		
		// Manage Patrol points
		maxDistance = 1;
		if (actor.getPatrolPoints().size()==0){
			return;
		}
		
		
		
		Point3D currentDestination = actor.getPatrolPoints().get(actor.getPatrolPointIndex());
		
		float relDistance = NGECore.getInstance().aiService.distanceSquared2D(currentPosition, currentDestination);
		
		if (currentDestination.getCell()!=null){
			 //Point3D curdestWS = core.simulationService.convertModelSpaceToPoint(currentDestination, creature.getGrandparent());
			Point3D curdestWS = currentDestination.getWorldPosition();
			 relDistance = NGECore.getInstance().aiService.distanceSquared2D(currentPosition, curdestWS);
		}

		if (relDistance<4){
		
			if (actor.getPatrolPointIndex()<actor.getPatrolPoints().size()-1){
				actor.setPatrolPointIndex(actor.getPatrolPointIndex()+1);
	
			} else {
				if (actor.isPatrolLoop()) {
					actor.setPatrolPointIndex(0);
				}
				else {
					String isInvader = (String) creature.getAttachment("IsInvader");
					if (isInvader!=null){

						// If invader check for in weapon range general
						if (core.invasionService.getDefensiveGeneral()!=null){
							if (core.invasionService.getInvasionPhase()==3 && core.invasionService.getDefensiveGeneral().getPosture()!=13 && core.invasionService.getDefensiveGeneral().getPosture()!=14 && core.invasionService.getDistanceToDefensiveGeneral(creature)<2500){
								actor.addDefender(core.invasionService.getDefensiveGeneral());
								return;
							}
						}
						
						// Check if there are more than 5 invaders at same spot
						if (NGECore.getInstance().simulationService.getAllNearSameFactionNPCs(7, creature).size()>=4 && NGECore.getInstance().invasionService.getInvasionPhase()!=3){
							actor.setAIactive(false); // switch off auto-target-recognition to counter lag
							actor.setCurrentState(new IdleState());
							//System.out.println("AI switched off!");
							return;						
						}
						
						//actor.setCurrentState(new IdleState());
						// Wait state() Since this state does not require move,recover and all that its simple task
	
						//NGECore.getInstance().aiService.waitForEvent(actor, NGECore.getInstance().invasionService, "isDefendingGeneralAlive", false, WithdrawalState.class);
						
						return; // Last Patrol point reached and no loop
					}
				}
			}
		} else {}
		
		Point3D newPosition = new Point3D();
		boolean foundNewPos = findNewPosition(actor, speed, maxDistance, newPosition);

		if(!foundNewPos || (newPosition.x == 0 && newPosition.z == 0))
			return;

		//Point3D newWorldPos = newPosition.getWorldPosition();
		Point3D newWorldPos = newPosition;
		if (newPosition.getCell()==null)
			newWorldPos = newPosition.getWorldPosition();
				
		currentPosition = creature.getPosition();
		
		float direction = (float) Math.atan2(newWorldPos.x - currentPosition.x, newWorldPos.z - currentPosition.z); // +0.973F
		if(direction < 0)
			direction = (float) (2 * Math.PI + direction); // +0.973F
		
		Quaternion quaternion = new Quaternion((float) Math.cos(direction / 2), 0, (float) Math.sin(direction / 2), 0);
        if (quaternion.y < 0.0f && quaternion.w > 0.0f) {
        	quaternion.y *= -1;
        	quaternion.w *= -1;
        }
        
        try{
        	core.simulationService.moveObject(creature, newPosition, quaternion, creature.getMovementCounter(), speed, newPosition.getCell());
        } catch (NullPointerException e) {
        	// Just to identify what exactly is null here
						//        	if (creature==null)
						//        		System.out.print("creature==null" );
						//        	if (newPosition==null)
						//        		System.out.print("newPosition==null" );
						//        	if (quaternion==null)
						//        		System.out.print("quaternion==null" );
						//        	if (newPosition.getCell()==null)
						//        		System.out.print("newPosition.getCell()==null" );
						//
        }
		
	}
	
	public void doWithdrawal(AIActor actor) {
		//NGECore.getInstance().aiService.logAI("AI STATE doPatrol");
		
		NGECore core = NGECore.getInstance();
		
		if(actor==null)
			return;		
		CreatureObject creature = actor.getCreature();
		if(creature==null)
			return;
		
//		if (creature.getPlanet().getName().equals("talus") && creature.getTemplate().contains("stormtrooper")){
//			System.out.println("actor " + actor.getActorID() + " stateID " + stateID + " creatureID"+ creature.getObjectID()+ " TIMEDIFF: " + (System.currentTimeMillis()-lastExecutionTime));
//			lastExecutionTime = System.currentTimeMillis();
//		}
		if(creature.getPosture() == 14 || creature.getPosture() == 13) {
			actor.setFollowObject(null);
			return;
		}
		
//		if (creature.isInCombat()){
//			return;
//		}
		
		
		TangibleObject target = actor.getFollowObject();
		float speed = (float) creature.getWalkSpeed();
		float maxDistance = 6;
		if(creature.getWeaponId() != 0) {
			WeaponObject weapon = (WeaponObject) core.objectService.getObject(creature.getWeaponId());
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		} else if(creature.getSlottedObject("default_weapon") != null) {
			WeaponObject weapon = (WeaponObject) creature.getSlottedObject("default_weapon");
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		}
		Point3D currentPosition = creature.getWorldPosition();
		
//		if(target != null && !core.simulationService.checkLineOfSight(creature, target))
//			maxDistance = 1;
		
		// Manage Patrol points
		maxDistance = 1;
		if (actor.getPatrolPoints().size()==0)
			return;
		if (actor.getPatrolPointIndex()<=0)
			actor.setPatrolPointIndex(0);
		Point3D currentDestination = actor.getPatrolPoints().get(actor.getPatrolPointIndex());
		//System.out.println("currentPosition.getDistance2D(currentDestination) " + currentPosition.getDistance2D(currentDestination));
		if (NGECore.getInstance().aiService.distanceSquared2D(currentPosition, currentDestination)<4){
		//if (currentPosition.getDistance2D(currentDestination)<4){
			if (actor.getPatrolPointIndex()>0){
				actor.setPatrolPointIndex(actor.getPatrolPointIndex()-1);
			} else {
				if (actor!=null)
					actor.destroyActor();
				if (creature!=null){
					// taken out due to double respawn, check invasions
//					core.simulationService.remove(creature, creature.getWorldPosition().x, creature.getWorldPosition().z, true); // Make sure
//					core.objectService.destroyObject(creature.getObjectID());	
				}
				return; // First Patrol point reached
			}
		}
		
		Point3D newPosition = new Point3D();
		boolean foundNewPos = findNewPosition(actor, speed, maxDistance, newPosition);
		
		
		
		
		if(!foundNewPos || (newPosition.x == 0 && newPosition.z == 0))
			return;
		
		Point3D newWorldPos = newPosition.getWorldPosition();
		float direction = (float) Math.atan2(newWorldPos.x - currentPosition.x, newWorldPos.z - currentPosition.z);
		if(direction < 0)
			direction = (float) (2 * Math.PI + direction);
		Quaternion quaternion = new Quaternion((float) Math.cos(direction / 2), 0, (float) Math.sin(direction / 2), 0);
        if (quaternion.y < 0.0f && quaternion.w > 0.0f) {
        	quaternion.y *= -1;
        	quaternion.w *= -1;
        }
        
//        if (newPosition.getCell()==null)
//        	System.out.println("newPosition.getCell() is NULL");
        try{
        	core.simulationService.moveObject(creature, newPosition, quaternion, creature.getMovementCounter(), speed, newPosition.getCell());
        } catch (NullPointerException e) {
        	// Just to identify what exactly is null here
//        	if (creature==null)
//        		System.out.print("creature==null" );
//        	if (newPosition==null)
//        		System.out.print("newPosition==null" );
//        	if (quaternion==null)
//        		System.out.print("quaternion==null" );
//        	if (newPosition.getCell()==null)
//        		System.out.print("newPosition.getCell()==null" );
//
//        	System.out.print("creature.getMovementCounter() " + creature.getMovementCounter());
        }
		
	}
	
	public void doLoiter(AIActor actor) {
		
		NGECore core = NGECore.getInstance();

		CreatureObject creature = actor.getCreature();
		if(creature.getPosture() == 14 || creature.getPosture() == 13) {
			actor.setFollowObject(null);
			return;
		}
		TangibleObject target = actor.getFollowObject();
		float speed = (float) creature.getWalkSpeed();
		float maxDistance = 6;
		if(creature.getWeaponId() != 0) {
			WeaponObject weapon = (WeaponObject) core.objectService.getObject(creature.getWeaponId());
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		} else if(creature.getSlottedObject("default_weapon") != null) {
			WeaponObject weapon = (WeaponObject) creature.getSlottedObject("default_weapon");
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		}
		Point3D currentPosition = creature.getWorldPosition();
		
		if(target != null && !core.simulationService.checkLineOfSight(creature, target))
			maxDistance = 1;
		
		// Manage Loiter points
		maxDistance = 1;
		long waitTime = 10000;
			
		Point3D currentDestination = actor.getLoiterDestination();
		if (actor.getLoiterDestType().equals("LOITER")){
			
			if (actor.getWaitState().equals("NO")){
				if (currentPosition.getDistance2D(currentDestination)<2){	
					actor.setWaitState("WAIT");
					actor.setWaitStartTime(System.currentTimeMillis());
				}
			}
			
			if (actor.getWaitState().equals("WAIT")){
				if (System.currentTimeMillis()-actor.getWaitStartTime()>waitTime){	
					actor.setWaitState("NO");
					currentDestination = actor.getOriginPosition();
					actor.setLoiterDestType("ORIGIN");
					actor.setLoiterDestination(actor.getOriginPosition());
				}
			}
			
//			if (currentPosition.getDistance2D(currentDestination)<2){				
//				// wait
//				core.scriptService.callScript("scripts/", "constructor_build_phase", "buildConstructor", core);
//				currentDestination = actor.getOriginPosition();
//				actor.setLoiterDestType("ORIGIN");
//				actor.setLoiterDestination(actor.getOriginPosition());
//			}
		} 
		if (actor.getLoiterDestType().equals("ORIGIN")){
			currentDestination = actor.getOriginPosition();
			
			if (actor.getWaitState().equals("NO")){
				if (currentPosition.getDistance2D(currentDestination)<2){	
					actor.setWaitState("WAIT");
					actor.setWaitStartTime(System.currentTimeMillis());
				}
			}
			
			if (actor.getWaitState().equals("WAIT")){
				if (System.currentTimeMillis()-actor.getWaitStartTime()>waitTime){	
					actor.setWaitState("NO");
					actor.setLoiterDestType("LOITER");	
					currentDestination = SpawnPoint.getRandomPosition(currentPosition, actor.getMinLoiterDist(), actor.getMaxLoiterDist(), creature.getPlanetId()); 
					actor.setLoiterDestination(currentDestination);
				}
			}
			
//			if (currentPosition.getDistance2D(currentDestination)<2){				
//				// wait
//				core.scriptService.callScript("scripts/", "constructor_build_phase", "buildConstructor", core);
//				actor.setLoiterDestType("LOITER");	
//				currentDestination = SpawnPoint.getRandomPosition(currentPosition, actor.getMinLoiterDist(), actor.getMaxLoiterDist(), creature.getPlanetId()); 
//				actor.setLoiterDestination(currentDestination);
//			}
		}
		
		Point3D newPosition = new Point3D();
		boolean foundNewPos = findNewLoiterPosition(actor, speed, maxDistance, newPosition);

		if(!foundNewPos || (newPosition.x == 0 && newPosition.z == 0))
			return;
		
		Point3D newWorldPos = newPosition.getWorldPosition();
		float direction = (float) Math.atan2(newWorldPos.x - currentPosition.x, newWorldPos.z - currentPosition.z);
		if(direction < 0)
			direction = (float) (2 * Math.PI + direction);
		Quaternion quaternion = new Quaternion((float) Math.cos(direction / 2), 0, (float) Math.sin(direction / 2), 0);
        if (quaternion.y < 0.0f && quaternion.w > 0.0f) {
        	quaternion.y *= -1;
        	quaternion.w *= -1;
        }
        
//        if (newPosition.getCell()==null)
//        	System.out.println("newPosition.getCell() is NULL");
        
        try{
        	core.simulationService.moveObject(creature, newPosition, quaternion, creature.getMovementCounter(), speed, newPosition.getCell());
        } catch (NullPointerException e) {
//        	// Just to identify what exactly is null here
//        	if (creature==null)
//        		System.out.print("creature==null" );
//        	if (newPosition==null)
//        		System.out.print("newPosition==null" );
//        	if (quaternion==null)
//        		System.out.print("quaternion==null" );
//        	if (newPosition.getCell()==null)
//        		System.out.print("newPosition.getCell()==null" );
//
//        	System.out.print("creature.getMovementCounter() " + creature.getMovementCounter());
        }
		
	}
	
	public void doFollow(AIActor actor) {
		//NGECore.getInstance().aiService.logAI("AI STATE doPatrol");
		NGECore core = NGECore.getInstance();

		CreatureObject creature = actor.getCreature();
		if(creature.getPosture() == 14 || creature.getPosture() == 13) {
			actor.setFollowObject(null);
			return;
		}
		TangibleObject target = actor.getFollowObject();
		float speed = (float) creature.getRunSpeed();
		float maxDistance = 6;
		if(creature.getWeaponId() != 0) {
			WeaponObject weapon = (WeaponObject) core.objectService.getObject(creature.getWeaponId());
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		} else if(creature.getSlottedObject("default_weapon") != null) {
			WeaponObject weapon = (WeaponObject) creature.getSlottedObject("default_weapon");
			if(weapon != null)
				maxDistance = weapon.getMaxRange() - 1;
		}
		Point3D currentPosition = creature.getWorldPosition();
		
		if(target != null && !core.simulationService.checkLineOfSight(creature, target))
			maxDistance = 1;
		
		// Manage Follow points
		maxDistance = 3;
		Point3D currentDestination = target.getWorldPosition();
		//System.out.println("currentPosition.getDistance2D(currentDestination)<1) " + currentPosition.getDistance2D(currentDestination));
		
		Point3D newPosition = new Point3D();
		boolean foundNewPos = findNewFollowPosition(actor, speed, maxDistance, newPosition);
		
		if(!foundNewPos || (newPosition.x == 0 && newPosition.z == 0))
			return;
		
		Point3D newWorldPos = newPosition.getWorldPosition();
		float direction = (float) Math.atan2(newWorldPos.x - currentPosition.x, newWorldPos.z - currentPosition.z);
		if(direction < 0)
			direction = (float) (2 * Math.PI + direction);
		Quaternion quaternion = new Quaternion((float) Math.cos(direction / 2), 0, (float) Math.sin(direction / 2), 0);
        if (quaternion.y < 0.0f && quaternion.w > 0.0f) {
        	quaternion.y *= -1;
        	quaternion.w *= -1;
        }
        
//        if (newPosition.getCell()==null)
//        	System.out.println("newPosition.getCell() is NULL");
        try{
        	core.simulationService.moveObject(creature, newPosition, quaternion, creature.getMovementCounter(), speed, newPosition.getCell());
        } catch (NullPointerException e) {
        	// Just to identify what exactly is null here
//        	if (creature==null)
//        		System.out.print("creature==null" );
//        	if (newPosition==null)
//        		System.out.print("newPosition==null" );
//        	if (quaternion==null)
//        		System.out.print("quaternion==null" );
//        	if (newPosition.getCell()==null)
//        		System.out.print("newPosition.getCell()==null" );
//
//        	System.out.print("creature.getMovementCounter() " + creature.getMovementCounter());
        }
		
	}

}
