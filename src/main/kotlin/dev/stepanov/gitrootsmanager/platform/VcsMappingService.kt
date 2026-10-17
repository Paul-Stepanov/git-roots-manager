package dev.stepanov.gitrootsmanager.platform

import dev.stepanov.gitrootsmanager.model.Mapping

/**
 * Единственная точка доступа плагина к VCS API платформы.
 * Все методы вызываются строго на EDT; переключение потока — обязанность
 * вызывающего.
 */
interface VcsMappingService {

    /**
     * Текущие directory mappings проекта; [Mapping.directory] нормализован
     * (абсолютный путь; пустая строка — корень проекта `<Project>`).
     */
    fun currentMappings(): List<Mapping>

    /**
     * Заменяет все directory mappings проекта на [mappings].
     */
    fun applyMappings(mappings: List<Mapping>)

    /**
     * Заносит [directory] в игнор-лист детектора незарегистрированных корней
     * (VcsConfiguration, workspace.xml), чтобы платформа не предлагала корень
     * снова.
     */
    fun ignoreRoot(directory: String)

    /**
     * Убирает [directory] из игнор-листа (при повторном включении корня).
     */
    fun unignoreRoot(directory: String)

    /**
     * Просит платформу повторно обнаружить VCS-корни (VcsRootScanner.scheduleScan).
     */
    fun requestRescan()
}
