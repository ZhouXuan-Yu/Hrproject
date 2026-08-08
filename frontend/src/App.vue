<template>
  <RouterView v-slot="{ Component, route }">
    <component
      v-if="route.meta.noCache"
      :is="Component"
      :key="route.fullPath"
    />
    <KeepAlive v-else :max="10">
      <component
        :is="Component"
        :key="route.fullPath"
      />
    </KeepAlive>
  </RouterView>
</template>

<style>
#app {
  width: 100%;
  min-height: 100vh;
}
</style>
