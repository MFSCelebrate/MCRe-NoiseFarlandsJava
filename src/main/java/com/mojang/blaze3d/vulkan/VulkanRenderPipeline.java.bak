package com.mojang.blaze3d.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSet;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputDivisorStateCreateInfoEXT;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDivisorDescriptionEXT;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.Buffer;

/**
 * Vulkan 渲染管线封装。
 * 每个实例对应一个 RenderPipeline，内部维护：
 * - 一条 Graphics Pipeline（深度状态通过动态状态控制）
 * - 一个 Descriptor Pool + 一个 Descriptor Set（用于高效绑定资源）
 * - Pipeline Layout 以及 Shader Module 句柄
 */
@OnlyIn(Dist.CLIENT)
public record VulkanRenderPipeline(
    RenderPipeline info,
    VulkanDevice device,
    long pipeline,              // 唯一的 Graphics Pipeline 句柄
    long pipelineLayout,        // Pipeline Layout 句柄
    VulkanBindGroupLayout layout, // 描述符布局（含条目信息）
    long vertexModule,          // Vertex Shader Module
    long fragmentModule,        // Fragment Shader Module
    long descriptorPool,        // 描述符池（每个管线独占一个，自动回收 Set）
    long descriptorSet          // 预分配的 Descriptor Set
) implements CompiledRenderPipeline, Destroyable {

    public static final long INVALID_PIPELINE = 0L;

    @Override
    public boolean isValid() {
        return this.pipeline != 0L;
    }

    /**
     * 编译 Vulkan 管线。
     *
     * @param device         Vulkan 设备
     * @param layout         描述符布局（由 GLSL 编译阶段生成）
     * @param pipeline       高层渲染管线描述（包含顶点格式、混合、深度等状态）
     * @param vertexModule   已编译的 Vertex Shader Module
     * @param fragmentModule 已编译的 Fragment Shader Module
     * @param pipelineCache  全局 Pipeline Cache 句柄（可为 0）
     * @return 编译好的 VulkanRenderPipeline 实例
     */
    public static VulkanRenderPipeline compile(
        final VulkanDevice device,
        final VulkanBindGroupLayout layout,
        final RenderPipeline pipeline,
        final long vertexModule,
        final long fragmentModule,
        final long pipelineCache
    ) {
        long pipelineLayout;
        long descriptorPool;
        long descriptorSet;

        // ============================================================
        // 1. 创建 Pipeline Layout
        // ============================================================
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pSetLayouts(stack.longs(layout.handle()));
            LongBuffer ptr = stack.callocLong(1);
            VulkanUtils.crashIfFailure(
                device,
                VK12.vkCreatePipelineLayout(device.vkDevice(), layoutInfo, null, ptr),
                "Failed to create pipeline layout for " + pipeline.getLocation()
            );
            pipelineLayout = ptr.get(0);
            device.instance().debug().setObjectName(
                device.vkDevice(), 17, pipelineLayout,
                () -> "Pipeline layout for " + pipeline.getLocation()
            );
        }

        // ============================================================
        // 2. 创建 Descriptor Pool（专用于此管线的一个 Set）
        // ============================================================
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 统计各种描述符数量
            int uboCount = 0;
            int samplerCount = 0;
            int texelCount = 0;
            for (VulkanBindGroupLayout.Entry entry : layout.entries()) {
                switch (entry.type()) {
                    case UNIFORM_BUFFER -> uboCount++;
                    case SAMPLED_IMAGE -> samplerCount++;
                    case TEXEL_BUFFER -> texelCount++;
                }
            }

            // 构建池大小数组（只包含非零类型）
            int poolSizeCount = (uboCount > 0 ? 1 : 0) + (samplerCount > 0 ? 1 : 0) + (texelCount > 0 ? 1 : 0);
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(poolSizeCount, stack);
            int pos = 0;
            if (uboCount > 0) {
                poolSizes.position(pos)
                    .sType$Default()
                    .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(uboCount);
                pos++;
            }
            if (samplerCount > 0) {
                poolSizes.position(pos)
                    .sType$Default()
                    .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(samplerCount);
                pos++;
            }
            if (texelCount > 0) {
                poolSizes.position(pos)
                    .sType$Default()
                    .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER)
                    .descriptorCount(texelCount);
                pos++;
            }
            poolSizes.position(0);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType$Default()
                .maxSets(1)
                .pPoolSizes(poolSizes);
            LongBuffer poolPtr = stack.callocLong(1);
            VulkanUtils.crashIfFailure(
                device,
                VK12.vkCreateDescriptorPool(device.vkDevice(), poolInfo, null, poolPtr),
                "Failed to create descriptor pool for " + pipeline.getLocation()
            );
            descriptorPool = poolPtr.get(0);
            device.instance().debug().setObjectName(
                device.vkDevice(), 16, descriptorPool,
                () -> "Descriptor pool for " + pipeline.getLocation()
            );

            // ============================================================
            // 3. 分配 Descriptor Set
            // ============================================================
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType$Default()
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(layout.handle()));
            LongBuffer setPtr = stack.callocLong(1);
            VulkanUtils.crashIfFailure(
                device,
                VK12.vkAllocateDescriptorSets(device.vkDevice(), allocInfo, setPtr),
                "Failed to allocate descriptor set for " + pipeline.getLocation()
            );
            descriptorSet = setPtr.get(0);
            device.instance().debug().setObjectName(
                device.vkDevice(), 15, descriptorSet,
                () -> "Descriptor set for " + pipeline.getLocation()
            );
        }

        // ============================================================
        // 4. 创建 Graphics Pipeline（只创建一条，深度全部动态控制）
        // ============================================================
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 4.1 Shader Stages
            Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            ByteBuffer mainName = stack.UTF8("main");
            VkPipelineShaderStageCreateInfo vertexStage = VkPipelineShaderStageCreateInfo.calloc(stack)
                .sType$Default()
                .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertexModule)
                .pName(mainName);
            VkPipelineShaderStageCreateInfo fragmentStage = VkPipelineShaderStageCreateInfo.calloc(stack)
                .sType$Default()
                .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragmentModule)
                .pName(mainName);
            shaderStages.put(vertexStage).put(fragmentStage).flip();

            // 4.2 Vertex Input
            VertexFormat[] vertexBindings = pipeline.getVertexFormatBindings();
            int totalBindings = vertexBindings.length;
            VkVertexInputAttributeDescription.Buffer attrDescs = VkVertexInputAttributeDescription.calloc(totalBindings, stack);
            VkVertexInputBindingDescription.Buffer bindingDescs = VkVertexInputBindingDescription.calloc(totalBindings, stack);
            VkVertexInputBindingDivisorDescriptionEXT.Buffer divisorDescs = VkVertexInputBindingDivisorDescriptionEXT.calloc(totalBindings, stack);
            int attribLocation = 0;

            for (int i = 0; i < totalBindings; i++) {
                VertexFormat format = vertexBindings[i];
                if (format == null) continue;

                VkVertexInputBindingDescription bindingDesc = VkVertexInputBindingDescription.calloc(stack)
                    .binding(i)
                    .stride(format.getVertexSize())
                    .inputRate(format.getStepRate() > 0 ? VK10.VK_VERTEX_INPUT_RATE_INSTANCE : VK10.VK_VERTEX_INPUT_RATE_VERTEX);
                bindingDescs.put(bindingDesc);

                if (format.getStepRate() > 0) {
                    VkVertexInputBindingDivisorDescriptionEXT divisorDesc = VkVertexInputBindingDivisorDescriptionEXT.calloc(stack)
                        .binding(i)
                        .divisor(format.getStepRate());
                    divisorDescs.put(divisorDesc);
                }

                for (VertexFormatElement element : format.getElements()) {
                    VkVertexInputAttributeDescription attrDesc = VkVertexInputAttributeDescription.calloc(stack)
                        .location(attribLocation)
                        .binding(i)
                        .offset(element.offset())
                        .format(VulkanConst.toVk(element.format()));
                    attrDescs.put(attrDesc);
                    attribLocation++;
                }
            }
            attrDescs.flip();
            bindingDescs.flip();
            divisorDescs.flip();

            VkPipelineVertexInputDivisorStateCreateInfoEXT divisorState = VkPipelineVertexInputDivisorStateCreateInfoEXT.calloc(stack)
                .sType$Default()
                .pVertexBindingDivisors(divisorDescs);
            VkPipelineVertexInputStateCreateInfo vertexInputState = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType$Default()
                .pVertexAttributeDescriptions(attrDescs)
                .pVertexBindingDescriptions(bindingDescs);
            if (divisorDescs.remaining() > 0) {
                vertexInputState.pNext(divisorState);
            }

            // 4.3 Input Assembly
            VkPipelineInputAssemblyStateCreateInfo inputAssemblyState = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType$Default()
                .topology(VulkanConst.toVk(pipeline.getPrimitiveTopology()));

            // 4.4 Rasterization
            VkPipelineRasterizationStateCreateInfo rasterizationState = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType$Default()
                .polygonMode(VulkanConst.toVk(pipeline.getPolygonMode()))
                .cullMode(pipeline.isCull() ? VK10.VK_CULL_MODE_BACK_BIT : VK10.VK_CULL_MODE_NONE)
                .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .lineWidth(1.0F);
            if (pipeline.getDepthStencilState() != null) {
                float biasConst = pipeline.getDepthStencilState().depthBiasConstant();
                float biasScale = pipeline.getDepthStencilState().depthBiasScaleFactor();
                rasterizationState.depthBiasEnable(biasConst != 0.0F || biasScale != 0.0F);
                rasterizationState.depthBiasConstantFactor(biasConst);
                rasterizationState.depthBiasSlopeFactor(biasScale);
            }

            // 4.5 Depth-Stencil（始终创建，全部使用动态状态）
            VkPipelineDepthStencilStateCreateInfo depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType$Default()
                .depthTestEnable(false)          // 运行时动态设置
                .depthWriteEnable(false)
                .depthCompareOp(VK10.VK_COMPARE_OP_ALWAYS)
                .depthBoundsTestEnable(false)
                .stencilTestEnable(false);

            // 4.6 Color Blend
            ColorTargetState[] colorTargets = pipeline.getColorTargetStates();
            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                VkPipelineColorBlendAttachmentState.calloc(colorTargets.length, stack);
            for (ColorTargetState cts : colorTargets) {
                VkPipelineColorBlendAttachmentState att = VkPipelineColorBlendAttachmentState.calloc(stack);
                att.colorWriteMask(cts != null ? VulkanConst.toVk(cts) : 0);
                if (cts != null && cts.blendFunction().isPresent()) {
                    applyBlendInformation(att, cts.blendFunction().get());
                }
                blendAttachments.put(att);
            }
            blendAttachments.flip();

            VkPipelineColorBlendStateCreateInfo colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType$Default()
                .pAttachments(blendAttachments);

            // 4.7 Viewport & Multisample（动态）
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType$Default()
                .viewportCount(1)
                .scissorCount(1);
            VkPipelineMultisampleStateCreateInfo multisampleState = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType$Default()
                .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .sampleShadingEnable(false);

            // 4.8 Dynamic States（Viewport + Scissor + 全套 Depth/Stencil）
            int[] dynamicStates = {
                VK10.VK_DYNAMIC_STATE_VIEWPORT,
                VK10.VK_DYNAMIC_STATE_SCISSOR,
                VK10.VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE,
                VK10.VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE,
                VK10.VK_DYNAMIC_STATE_DEPTH_COMPARE_OP,
                VK10.VK_DYNAMIC_STATE_DEPTH_BOUNDS,
                VK10.VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK,
                VK10.VK_DYNAMIC_STATE_STENCIL_WRITE_MASK,
                VK10.VK_DYNAMIC_STATE_STENCIL_REFERENCE
            };
            VkPipelineDynamicStateCreateInfo dynamicStateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType$Default()
                .pDynamicStates(stack.ints(dynamicStates));

            // 4.9 Rendering Info（深度格式固定为 D32_FLOAT）
            VkPipelineRenderingCreateInfoKHR renderingInfo = VkPipelineRenderingCreateInfoKHR.calloc(stack)
                .sType$Default();
            IntBuffer colorFormats = stack.mallocInt(colorTargets.length);
            for (int i = 0; i < colorTargets.length; i++) {
                ColorTargetState cts = colorTargets[i];
                colorFormats.put(i, cts != null ? VulkanConst.toVk(cts.format()) : VK10.VK_FORMAT_UNDEFINED);
            }
            renderingInfo.pColorAttachmentFormats(colorFormats);
            // 总是使用 D32_FLOAT，运行时通过动态状态控制是否启用深度测试
            renderingInfo.depthAttachmentFormat(VulkanConst.toVk(GpuFormat.D32_FLOAT));

            // 4.10 组装创建信息
            VkGraphicsPipelineCreateInfo.Buffer createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType$Default()
                .pStages(shaderStages)
                .pVertexInputState(vertexInputState)
                .pInputAssemblyState(inputAssemblyState)
                .pRasterizationState(rasterizationState)
                .pDepthStencilState(depthStencilState)
                .pColorBlendState(colorBlendState)
                .pViewportState(viewportState)
                .pMultisampleState(multisampleState)
                .pDynamicState(dynamicStateInfo)
                .layout(pipelineLayout)
                .pNext(renderingInfo);

            LongBuffer pipelinePtr = stack.callocLong(1);
            int result = VK12.vkCreateGraphicsPipelines(
                device.vkDevice(),
                pipelineCache,   // 使用传入的 Cache
                createInfo,
                null,
                pipelinePtr
            );
            VulkanUtils.crashIfFailure(device, result, "Failed to create graphics pipeline for " + pipeline.getLocation());

            long pipelineHandle = pipelinePtr.get(0);
            device.instance().debug().setObjectName(
                device.vkDevice(), 19, pipelineHandle,
                () -> "Pipeline " + pipeline.getLocation()
            );

            return new VulkanRenderPipeline(
                pipeline,
                device,
                pipelineHandle,
                pipelineLayout,
                layout,
                vertexModule,
                fragmentModule,
                descriptorPool,
                descriptorSet
            );
        }
    }

    /**
     * 销毁所有 Vulkan 对象。
     * 注意：Shader Module 由外部管理，此处不销毁（它们可能被多个管线共享）。
     * 但根据当前设计，每个管线编译时会生成独立的 Module，所以在这里销毁是安全的。
     * 如果将来改为共享 Module，则需要调整销毁逻辑。
     */
    @Override
    public void destroy() {
        if (this.pipeline != 0L) {
            VK12.vkDestroyPipeline(this.device.vkDevice(), this.pipeline, null);
            VK12.vkDestroyPipelineLayout(this.device.vkDevice(), this.pipelineLayout, null);
            // 销毁 Descriptor Pool 会自动回收 Descriptor Set
            VK12.vkDestroyDescriptorPool(this.device.vkDevice(), this.descriptorPool, null);
            VK12.vkDestroyDescriptorSetLayout(this.device.vkDevice(), this.layout.handle(), null);
            VK12.vkDestroyShaderModule(this.device.vkDevice(), this.vertexModule, null);
            VK12.vkDestroyShaderModule(this.device.vkDevice(), this.fragmentModule, null);
        }
    }

    /**
     * 将 BlendFunction 应用到 VkPipelineColorBlendAttachmentState。
     */
    private static void applyBlendInformation(
        final VkPipelineColorBlendAttachmentState att,
        final BlendFunction blendFunction
    ) {
        att.blendEnable(true)
            .colorBlendOp(VulkanConst.toVk(blendFunction.color().op()))
            .alphaBlendOp(VulkanConst.toVk(blendFunction.alpha().op()))
            .dstAlphaBlendFactor(VulkanConst.toVk(blendFunction.alpha().destFactor()))
            .dstColorBlendFactor(VulkanConst.toVk(blendFunction.color().destFactor()))
            .srcAlphaBlendFactor(VulkanConst.toVk(blendFunction.alpha().sourceFactor()))
            .srcColorBlendFactor(VulkanConst.toVk(blendFunction.color().sourceFactor()));
    }
}