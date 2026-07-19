package net.minecraft.world.entity;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class SteppedInterpolationHandler extends InterpolationHandler {
   private final SteppedInterpolationHandler.InterpolationData interpolationData = new SteppedInterpolationHandler.InterpolationData();
   private final SteppedInterpolationTracker interpolationTracker;

   public SteppedInterpolationHandler(final Entity entity) {
      super(entity);
      this.interpolationTracker = new SteppedInterpolationTracker(entity);
   }

   @Override
   public InterpolationTracker interpolationTracker() {
      return this.interpolationTracker;
   }

   @Override
   protected PositionAndRotation.Mutable interpolationData() {
      return this.interpolationData;
   }

   @Override
   protected void startInterpolating(final PositionPath position, final float yRot, final float xRot) {
      Vec3 endPosition = position.endPosition();
      if (!Objects.equals(endPosition, this.interpolationData.position())) {
         this.interpolationData.addSteps(position, this.entity.position(), this.interpolationSteps);
      }

      int rotationSteps = this.interpolationSteps;
      if (position instanceof PositionPath.Stepped var6) {
         PositionPath.Stepped var10000 = var6;

         try {
            var10000.endPosition();
         } catch (Throwable var11) {
            throw new MatchException(var11.toString(), var11);
         }

         var10000 = var6;

         try {
            var14 = var10000.steps();
         } catch (Throwable var10) {
            throw new MatchException(var10.toString(), var10);
         }

         List var8 = var14;
         List<PositionStep> steps = var8;
         rotationSteps = 0;

         for (PositionStep step : steps) {
            rotationSteps += step.tickOffset();
         }
      }

      this.interpolationData.set(endPosition, yRot, xRot);
      this.interpolationData.remainingRotationSteps = rotationSteps;
   }

   @Override
   protected void doInterpolate() {
      Vec3 newPosition = this.interpolationData.getNewPosition();
      float alpha = 1.0F / Math.max(this.interpolationData.remainingRotationSteps, 1.0F);
      float newYRot = Mth.rotLerp(alpha, this.entity.getYRot(), this.interpolationData.yRot());
      float newXRot = Mth.lerp(alpha, this.entity.getXRot(), this.interpolationData.xRot());
      this.entity.setPos(newPosition);
      this.entity.setRot(newYRot, newXRot);
      float tick = this.entity.level().getRelativeTickSpeed();
      this.interpolationData.currentStepTicks += tick;
      this.interpolationData.remainingRotationSteps -= tick;
   }

   @Override
   public void applyPredictedMovement(final Vec3 delta) {
      super.applyPredictedMovement(delta);
      if (!this.entity.level().isClientSide()) {
         this.interpolationTracker.applyPredictedMovement(delta);
      }
   }

   @Override
   public boolean hasActiveInterpolation() {
      return this.interpolationData.remainingRotationSteps > 0.0F || !this.interpolationData.remainingSteps.isEmpty();
   }

   @Override
   public void cancel() {
      this.interpolationData.remainingSteps.clear();
      this.interpolationData.remainingRotationSteps = 0.0F;
   }

   private static class InterpolationData extends PositionAndRotation.Mutable {
      private final LinkedList<PositionStep> remainingSteps = new LinkedList<>();
      private Vec3 lastStepPosition = Vec3.ZERO;
      private float currentStepTicks;
      private float remainingRotationSteps;

      @Override
      public void addDelta(final Vec3 delta) {
         super.addDelta(delta);
         this.remainingSteps.replaceAll(step -> step.addDelta(delta));
         this.lastStepPosition = this.lastStepPosition.add(delta);
      }

      private void addSteps(final PositionPath position, final Vec3 startingPosition, final int interpolationSteps) {
         if (this.remainingSteps.isEmpty()) {
            this.lastStepPosition = startingPosition;
            this.currentStepTicks = 1.0F;
         }

         switch (position) {
            case PositionPath.Linear(Vec3 pos):
               this.remainingSteps.add(new PositionStep(pos, interpolationSteps));
               break;
            case PositionPath.Stepped var8:
               PositionPath.Stepped var10000 = var8;

               try {
                  var10000.endPosition();
               } catch (Throwable var12) {
                  throw new MatchException(var12.toString(), var12);
               }

               var10000 = var8;

               try {
                  var16 = var10000.steps();
               } catch (Throwable var11) {
                  throw new MatchException(var11.toString(), var11);
               }

               List var10 = var16;
               List<PositionStep> steps = var10;
               this.remainingSteps.addAll(steps);
               break;
            default:
               throw new MatchException(null, null);
         }
      }

      private Vec3 getNewPosition() {
         while (!this.remainingSteps.isEmpty()) {
            PositionStep step = this.remainingSteps.getFirst();
            int offset = step.tickOffset();
            if (this.currentStepTicks < offset) {
               double a = this.currentStepTicks / offset;
               return this.lastStepPosition.lerp(step.position(), a);
            }

            this.currentStepTicks -= offset;
            this.lastStepPosition = step.position();
            this.remainingSteps.removeFirst();
         }

         return this.position();
      }
   }
}
