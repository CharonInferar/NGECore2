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
package services.ai;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.Vector;

import resources.common.SpawnPoint;
import resources.datatables.Difficulty;
import resources.datatables.FactionStatus;
import resources.datatables.GcwType;
import resources.objects.building.BuildingObject;
import resources.objects.cell.CellObject;
import resources.objects.creature.CreatureObject;
import resources.objects.group.GroupObject;
import resources.objects.player.PlayerObject;
import resources.objects.tangible.TangibleObject;
import services.ai.states.AIState;
import services.ai.states.IdleState;
import services.ai.states.LoiterState;
import services.ai.states.PatrolState;
import engine.resources.objects.SWGObject;
import engine.resources.scene.Point3D;
import engine.resources.service.INetworkDispatch;
import main.NGECore;

public class AIService {
	
	CreatureObject corpse = null;
	
	@SuppressWarnings("unused") private Vector<AIActor> aiActors = new Vector<AIActor>();
	private NGECore core;
	private TangibleObject checkerAI = null;
	
	public AIService(NGECore core) {
		this.core = core;
	}
	
	public Vector<Point3D> findPath(int planetId, Point3D pointA, Point3D pointB) {
		
		// TODO: implement cell pathfinding, returning straight line for now
		Vector<Point3D> path = new Vector<Point3D>();
		if (pointA==null || pointB==null)
			return path;		
		path.add(pointA);
		float x = pointB.x - 1 + new Random().nextFloat();
		float z = pointB.z - 1 + new Random().nextFloat();
		Point3D endPoint = new Point3D(x, core.terrainService.getHeight(planetId, x, z), z);
		endPoint.setCell(pointB.getCell());
		if(endPoint.getCell() != null)
			endPoint.y = pointB.y;
		path.add(endPoint);
		return path;
	}
	
	public void awardExperience(AIActor actor) {
		
		Map<CreatureObject, Integer> damageMap = actor.getDamageMap();
		CreatureObject creature = actor.getCreature();
		int baseXp = getBaseXP(creature);
		for(Entry<CreatureObject, Integer> e : damageMap.entrySet()) {
			
			CreatureObject player = e.getKey();
			PlayerObject ghost = (PlayerObject) player.getSlottedObject("ghost");
			if(ghost == null)
				continue;
			int damageDealt = e.getValue();
			
			short level = (player.getGroupId() == 0) ? player.getLevel() : ((GroupObject) core.objectService.getObject(player.getGroupId())).getGroupLevel();
			int levelDifference = ((creature.getLevel() >= level) ? 0 : (level - creature.getLevel()));
			int damagePercent = ((damageDealt / creature.getMaxHealth()) * 100);
			int finalXp = (((damagePercent / 100) * baseXp) + (creature.getMaxHealth() / 12));
			finalXp -= ((levelDifference > 20) ? (finalXp - 1) : (((levelDifference * 5) / 100) * finalXp));	
			core.playerService.giveExperience(player, finalXp);
		}
		
	}
	
	public int getBaseXP(CreatureObject creature) {
		
		int difficulty = creature.getDifficulty();
		int baseXP = 60;
		for (int i = 2; i <= creature.getLevel(); i++) {
			
			if(i < 25)
				baseXP += 3;
			else if(i < 50)
				baseXP += 4;
			else if(i < 75)
				baseXP += 5;
			else if(i < 100)
				baseXP += 6;
			else
				baseXP += 7;

		}
		
		
		//TODO: this is slightly inaccurate if the xp table in the prima guide is correct
		if(difficulty == 1) {
			baseXP += (6 + ((creature.getLevel() - 1) / 10) * 3);
		} else if(difficulty == 2) {
			baseXP += (20 + (creature.getLevel() - 1));
		}
		
		return baseXP;
		
	}
	
	public void awardGcw(AIActor actor) {
		CreatureObject npc = actor.getCreature();
		
		if (core.factionService.isPvpFaction(npc.getFaction())) {
			int gcwPoints = 5;
			
			if (npc.getDifficulty() == Difficulty.ELITE) {
				gcwPoints *= 2;
			}
			
			if (npc.getDifficulty() == Difficulty.BOSS) {
				gcwPoints *= 5;
			}
			
			//gcwPoints = actor.getMobileTemplate().getGcwPoints(); // We might want to make this get set in mobile templates if it was different for different npcs ie. assault squads.
			
			for (CreatureObject player : actor.getDamageMap().keySet()) {
				if (player.getGroupId() == 0) {
					if (player.getFaction().length() > 0 && player.getFactionStatus() > FactionStatus.OnLeave) {
						if ((npc.getLevel() / player.getLevel() * 100) < 86) {
							continue;
						}
						
						core.gcwService.addGcwPoints(player, gcwPoints, GcwType.Enemy);
					}
				} else {
					for (SWGObject object : ((GroupObject) core.objectService.getObject(player.getGroupId())).getMemberList()) {
						CreatureObject member = (CreatureObject) object;
						
						if (member == null) {
							continue;
						}
						
						if (npc.getPlanet().getName().equals(member.getPlanet().getName()) && npc.getPosition().getDistance(member.getPosition()) > 300) {
							continue;
						}
						
						if ((npc.getLevel() / member.getLevel() * 100) < 86) {
							continue;
						}
						
						if (member.getFaction().length() > 0 && member.getFactionStatus() > FactionStatus.OnLeave) {
							core.gcwService.addGcwPoints(member, gcwPoints, GcwType.Enemy);
						}
					}
				}
			}
		}
	}
	
	public void setPatrolLoop(CreatureObject creature, boolean value){
		AIActor actor = (AIActor) creature.getAttachment("AI");
		if (actor==null)
			return;		
		actor.setPatrolLoop(value);
	}
	
	public void setPatrol(CreatureObject creature, Vector<Point3D> patrolpoints){
		AIActor actor = (AIActor) creature.getAttachment("AI");
		if (actor==null)
			return;
		
		actor.setPatrolPoints(patrolpoints);
		AIState intendedPrimaryAIState = new PatrolState();
		actor.setIntendedPrimaryAIState(intendedPrimaryAIState);
		actor.setCurrentState(intendedPrimaryAIState);	
		actor.setCurrentState(intendedPrimaryAIState);
		actor.setCurrentState(intendedPrimaryAIState);
	}
	
	public void setPatrol(CreatureObject creature, boolean active){
		AIActor actor = (AIActor) creature.getAttachment("AI");
		if (actor==null)
			return;
		
		if (active){
			AIState intendedPrimaryAIState = new PatrolState();
			actor.setIntendedPrimaryAIState(intendedPrimaryAIState);
			actor.setCurrentState(intendedPrimaryAIState);
		}
		else
			actor.setCurrentState(new IdleState());
	}
	
	public void addPatrolPointCell(Vector<Point3D> patrolpoints, BuildingObject building, Point3D position, int cellID){
		if (building==null)
			return;
		CellObject cellObj = building.getCellByCellNumber(cellID);
		if (cellObj!=null)
			position.setCell(cellObj);
		patrolpoints.add(position);
	}
	
	public void setLoiter(CreatureObject creature, float minDist, float maxDist){
		AIActor actor = (AIActor) creature.getAttachment("AI");
		if (actor==null)
			return;
		actor.setOriginPosition(creature.getWorldPosition());
		Point3D currentDestination = SpawnPoint.getRandomPosition(creature.getWorldPosition(), minDist, maxDist, creature.getPlanetId()); 
		actor.getMovementPoints().add(currentDestination);
		actor.setLoiterDestination(currentDestination);
		actor.setMinLoiterDist(minDist);
		actor.setMaxLoiterDist(maxDist);
		AIState intendedPrimaryAIState = new LoiterState();
		actor.setIntendedPrimaryAIState(intendedPrimaryAIState);	
		actor.setCurrentState(intendedPrimaryAIState);
	}	
	
	public void setCheckAI(TangibleObject checker){
		checkerAI = checker;
	}
	
	public TangibleObject getCheckAI(){
		return this.checkerAI;
	}
	
	public float distanceSquared2D(Point3D p2, Point3D p1){
		return (p2.x - p1.x) * (p2.x - p1.x) + (p2.z - p1.z) * (p2.z - p1.z);
	}
	
	public float distanceSquared(Point3D p2, Point3D p1){
		return (p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y) + (p2.z - p1.z) * (p2.z - p1.z);
	}
	
	public void waitForEvent(AIActor actor, INetworkDispatch service, String serviceMethodName, boolean expectedValue, Class<?> nextStateClass){
		
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);	
		final Future<?>[] wfe = {null};
		wfe[0] = scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					Class<?> noParams[] = {};
					Method m = service.getClass().getMethod(serviceMethodName, noParams);
					boolean value = (boolean) m.invoke(service); 					
					if (value==expectedValue){
						actor.setCurrentState((AIState)nextStateClass.newInstance());
						System.out.println("condition true waitForEvent! " + nextStateClass.getName());
						Thread.yield();
		                wfe[0].cancel(false);
					}					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}			
		}, 10, 3000, TimeUnit.MILLISECONDS);
	}
	
	public void waitForEvent(AIActor actor, INetworkDispatch service, String serviceMethodName, int expectedValue, Class<?> nextStateClass){
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);	
		final Future<?>[] wfe = {null};
		wfe[0] = scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					Class<?> noParams[] = {};
					Method m = service.getClass().getMethod(serviceMethodName, noParams);
					int number = (int) m.invoke(service); 					
					if (number==expectedValue){
						actor.setCurrentState((AIState)nextStateClass.newInstance());
						Thread.yield();
		                wfe[0].cancel(false);
					}					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}			
		}, 10, 3000, TimeUnit.MILLISECONDS);
	}
	
	// ToDo: Make an overloaded method with params
	public void waitForEvent(INetworkDispatch service, String serviceMethodName, Object[] params){
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);	
		final Future<?>[] wfe = {null};
		wfe[0] = scheduler.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				try {
					boolean condition = true;
					Class<?> noParams[] = {};
					Method m = service.getClass().getMethod(serviceMethodName, noParams);
					m.invoke(service); 
					
					if (condition){
						Thread.yield();
		                wfe[0].cancel(false);
					}
					// do something
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
		}, 10, 2000, TimeUnit.MILLISECONDS);
	}
	
<<<<<<< HEAD
	public void startBreadCrumbTrail(CreatureObject target){
		
		if (target.isLeavingTrail())
			return;
		target.setLeavingTrail(true);
		target.clearBreadCrumbTrail();
		
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);	
		final Future<?>[] bct = {null};
		bct[0] = scheduler.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				try {

					if (!target.isLeavingTrail()){
						Thread.yield();
						bct[0].cancel(false);
					}

					Point3D targetPosition = new Point3D();
			
					if (target.getContainer()!=null && target.getContainer() instanceof CellObject){
						CellObject cell = (CellObject)target.getContainer();
						if (cell!=null){
							targetPosition.setCell(cell);
							target.addPositionToBreadCrumbTrail(target.getPosition());
						}
					} else {
						target.addPositionToBreadCrumbTrail(target.getWorldPosition());
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
		}, 0, 1000, TimeUnit.MILLISECONDS);
	}
	
	public Point3D findClosestBreadCrumb(CreatureObject NPC, CreatureObject target){
		
		Point3D closestBradCrumb = null;
		
		if (!target.isLeavingTrail())
			return null;
		
		float minDist = 9999F;
		int newIndex = 9999;
		
		Vector<Point3D> trail = target.getBreadCrumbTrail();
		for (int i=0;i<trail.size();i++){
			Point3D point = trail.get(i);
			Point3D NPCpos = NPC.getWorldPosition();
			int lastindex = ((AIActor) NPC.getAttachment("AI")).getLastTrailIndex();		
			if (point.getCell()!=null)
				NPCpos = NPC.getPosition();
			if (distanceSquared2D(NPCpos,point)<minDist && lastindex<i && distanceSquared2D(NPCpos,point)>1.2){
				minDist = distanceSquared2D(NPCpos,point);
				closestBradCrumb = point;
				newIndex = i;
			}
		}
		if (newIndex!=9999)
			((AIActor) NPC.getAttachment("AI")).setLastTrailIndex(newIndex);
		return closestBradCrumb;
	}

	
=======
	// Test method
	public void explicitDestroy(CreatureObject actor){
		System.out.println("SEND DESTROY SENDING for " + corpse.getTemplate());
		corpse.sendDestroy(actor.getClient());
		System.out.println("SEND DESTROY SENT ");
	}
	
>>>>>>> origin/master
	public void logAI(String logMsg){
		if (checkDeveloperIdentity()){
			System.err.println("AI-LOG: " + logMsg);
		}
	}
	
	public boolean checkDeveloperIdentity(){
		if (System.getProperty("user.name").equals("Charon"))
			return true;
		return false;
	}

	public CreatureObject getCorpse() {
		return corpse;
	}

	public void setCorpse(CreatureObject corpse) {
		System.out.println("SET CORPSE " + corpse.getTemplate());
		this.corpse = corpse;
	}
}
