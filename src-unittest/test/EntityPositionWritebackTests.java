package test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔧 MCRe 单元测试：<b>位置写回约定守卫</b>（源码级断言）。
 *
 * <p>为什么用源码断言而不是行为测试：踩过的坑是「**坐标读起来对、但碰撞箱不跟随**」——
 * {@code Entity.setPosRaw} <b>不重建 AABB</b>（只有 {@code setPos} 会调 {@code setBoundingBox(makeBoundingBox())}），
 * 而碰撞/物理用的是缓存的 AABB → 用 {@code setPosRaw} 写回位置会让碰撞箱停在旧位置 →
 * 玩家「踩着空气走」。行为测试要造出完整 Entity/Level，成本远高于这条约定断言；
 * 这里直接盯住「精确写回必须走 {@code setPos}」这条约定，把回归钉死。
 */
class EntityPositionWritebackTests {

    private static final Path ENTITY_SOURCE = Path.of("src/common/net/minecraft/world/entity/Entity.java");

    private static String readEntitySource() throws Exception {
        return Files.readString(ENTITY_SOURCE, StandardCharsets.UTF_8);
    }

    /** 取出 {@code move(MoverType, Vec3)} 方法体（按大括号配对）。 */
    private static String moveBody(final String source) {
        final int signature = source.indexOf("public void move(final MoverType moverType");
        assertTrue(signature > 0, "未找到 Entity.move(MoverType, Vec3) 方法");

        final int open = source.indexOf('{', signature);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;

                if (depth == 0) {
                    return source.substring(open, i + 1);
                }
            }
        }

        throw new AssertionError("Entity.move 方法体括号不配对");
    }

    @Test
    @DisplayName("move() 的精确写回必须走 setPos（setPosRaw 不重建 AABB → 碰撞箱不跟随）")
    void preciseWritebackMustUseSetPos() throws Exception {
        final String body = moveBody(readEntitySource());

        assertFalse(
                body.contains("setPosRaw(precise"),
                "Entity.move 的精确写回用了 setPosRaw → 碰撞箱（AABB）不会重建 → 玩家碰撞箱与实体分离"
        );
        assertTrue(
                body.contains("setPos(precise"),
                "Entity.move 的精确写回应走 setPos（它才会 setBoundingBox(makeBoundingBox())）"
        );
    }

    @Test
    @DisplayName("AABB 只在 setPos 里重建（约定守卫：别把重建逻辑挪走）")
    void boundingBoxIsRebuiltInSetPos() throws Exception {
        final String source = readEntitySource();
        final int setPos = source.indexOf("public void setPos(final double x, final double y, final double z)");
        assertTrue(setPos > 0, "未找到 Entity.setPos(double,double,double)");

        final int open = source.indexOf('{', setPos);
        int depth = 0;
        int end = -1;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;

                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }

        assertTrue(end > 0, "Entity.setPos 方法体括号不配对");
        assertTrue(
                source.substring(open, end).contains("setBoundingBox"),
                "Entity.setPos 必须重建 AABB（碰撞/物理依赖缓存的包围盒）"
        );
    }
}
