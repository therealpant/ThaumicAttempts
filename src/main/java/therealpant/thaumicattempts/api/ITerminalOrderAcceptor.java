package therealpant.thaumicattempts.api;

/**
 * Общий контракт для блоков, которыми можно управлять из Order Terminal.
 */
public interface ITerminalOrderAcceptor {
    /**
     * Запустить слот с терминала (крафт/заказ ресурсов и т. д.).
     *
     * @param slot  индекс паттерна/слота предпросмотра
     * @param count количество раз
     */
    void triggerFromTerminal(int slot, int count);
}